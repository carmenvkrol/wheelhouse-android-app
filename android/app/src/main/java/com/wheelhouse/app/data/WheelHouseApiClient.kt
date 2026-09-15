package com.wheelhouse.app.data

import com.wheelhouse.app.ui.home.Attribution
import com.wheelhouse.app.ui.home.AttributionDriver
import com.wheelhouse.app.ui.home.AttributionSplit
import com.wheelhouse.app.ui.home.Banded
import com.wheelhouse.app.ui.home.BandedPrice
import com.wheelhouse.app.ui.home.Capital
import com.wheelhouse.app.ui.home.DecisionCard
import com.wheelhouse.app.ui.home.DecisionOption
import com.wheelhouse.app.ui.home.ExitClock
import com.wheelhouse.app.ui.home.ExitType
import com.wheelhouse.app.ui.home.OptionKind
import com.wheelhouse.app.ui.home.OptionLeg
import com.wheelhouse.app.ui.home.OptionRight
import com.wheelhouse.app.ui.home.PnlWindow
import com.wheelhouse.app.ui.home.PositionSummary
import com.wheelhouse.app.ui.home.StockLeg
import com.wheelhouse.app.ui.positions.AutoExitNote
import com.wheelhouse.app.ui.positions.BoardOptionLeg
import com.wheelhouse.app.ui.positions.BoardStockLeg
import com.wheelhouse.app.ui.positions.IvContext
import com.wheelhouse.app.ui.positions.PositionDetailState
import com.wheelhouse.app.ui.positions.PositionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Talks to api/mock-server/ over plain HTTP and maps its CONTRACT.md-shaped JSON onto
 * the UI state types the screens already render (HomeDashboardScreen.kt,
 * PositionsScreen.kt, PositionDetailScreen.kt, RiskScreen.kt) — no DTO layer of its own,
 * since the screens' own models already are the app's understanding of the contract.
 *
 * Plain OkHttp + org.json rather than Retrofit/Moshi: eight endpoints against a server
 * that only exists as a local dev stand-in isn't enough surface to earn a
 * request-builder framework. Every call can throw [IOException] on a network problem or
 * unexpected shape — callers decide what "can't reach the mock" should look like
 * (MainActivity falls back to the local sample*State() fixtures, same as always).
 */
object WheelHouseApiClient {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ── Public surface ──────────────────────────────────────────────────────

    data class Status(val engine: String, val dataAgeS: Int, val paused: Boolean, val book: String)

    suspend fun fetchStatus(): Status {
        val json = getObject("/v1/status")
        val books = json.getJSONArray("books")
        return Status(
            engine = json.getString("engine"),
            dataAgeS = json.getInt("data_age_s"),
            paused = json.getBoolean("paused"),
            book = if (books.length() > 0) books.getString(0) else "BASE",
        )
    }

    data class PnlSnapshot(val windows: List<PnlWindow>, val capital: Capital)

    suspend fun fetchPnl(): PnlSnapshot {
        val json = getObject("/v1/pnl")
        val windows = json.getJSONObject("windows")
        fun window(key: String, label: String) = PnlWindow(
            label = label,
            realized = parseBandedInt(windows.getJSONObject(key).getJSONObject("realized")),
            unrealized = parseBandedInt(windows.getJSONObject(key).getJSONObject("unrealized")),
        )
        return PnlSnapshot(
            windows = listOf(window("today", "Today"), window("wtd", "Week"), window("mtd", "Month"), window("ytd", "Year")),
            capital = Capital(
                target = json.getInt("target_capital"),
                deployed = json.getInt("deployed"),
                idle = json.getInt("idle"),
            ),
        )
    }

    data class RiskSnapshot(
        val assignAllCost: Int,
        val cashMarginAvailable: Int,
        val marginUtilizationPct: Int,
        val netExposure: Int,
        val netExposureMeterPct: Int,
        val syntheticCash: Int,
        val syntheticCashMeterPct: Int,
        val concentration: List<Pair<String, Int>>,
    )

    suspend fun fetchRisk(): RiskSnapshot {
        val json = getObject("/v1/risk")
        val concentrationJson = json.getJSONArray("concentration")
        val concentration = (0 until concentrationJson.length()).map {
            val slice = concentrationJson.getJSONObject(it)
            slice.getString("underlying") to slice.getInt("pct")
        }
        return RiskSnapshot(
            assignAllCost = json.getInt("assign_all_cost"),
            cashMarginAvailable = json.getInt("cash_margin_available"),
            marginUtilizationPct = json.getInt("margin_utilization_pct"),
            netExposure = json.optInt("net_exposure", json.getInt("assign_all_cost")),
            netExposureMeterPct = json.optInt("net_exposure_meter_pct", json.getInt("margin_utilization_pct")),
            syntheticCash = json.optInt("synthetic_cash", 0),
            syntheticCashMeterPct = json.optInt("synthetic_cash_meter_pct", 0),
            concentration = concentration,
        )
    }

    data class PositionsSnapshot(
        val boardRows: List<PositionRow>,
        val dashboardSummaries: List<PositionSummary>,
        val detailByTicker: Map<String, PositionDetailState>,
    )

    suspend fun fetchPositions(): PositionsSnapshot {
        val array = getArray("/v1/positions")
        val boardRows = mutableListOf<PositionRow>()
        val summaries = mutableListOf<PositionSummary>()
        val details = mutableMapOf<String, PositionDetailState>()
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            boardRows.add(parsePositionRow(row))
            parsePositionSummaryOrNull(row)?.let { summaries.add(it) }
            parsePositionDetailOrNull(row)?.let { details[row.getString("ticker")] = it }
        }
        return PositionsSnapshot(boardRows, summaries, details)
    }

    suspend fun fetchDecisions(): List<DecisionCard> {
        val array = getArray("/v1/decisions")
        return (0 until array.length()).map { parseDecisionCard(array.getJSONObject(it)) }
    }

    /** Throws [IOException] with the server's error message on a 4xx/5xx (e.g. a missing
     *  reason on a `requires_reason` option) — callers should surface that, not swallow it. */
    suspend fun resolveDecision(decisionId: String, optionId: String, reason: String?) {
        val payload = JSONObject().put("option_id", optionId)
        if (reason != null) payload.put("reason", reason)
        postObject("/v1/decisions/$decisionId", payload)
    }

    suspend fun setPaused(paused: Boolean): Boolean {
        val json = postObject(if (paused) "/v1/pause" else "/v1/resume", JSONObject())
        return json.getBoolean("paused")
    }

    // ── HTTP plumbing ───────────────────────────────────────────────────────

    private suspend fun getObject(path: String): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(executeAndReadBody(Request.Builder().url(ApiConfig.BASE_URL + path).get().build(), path))
    }

    private suspend fun getArray(path: String): JSONArray = withContext(Dispatchers.IO) {
        JSONArray(executeAndReadBody(Request.Builder().url(ApiConfig.BASE_URL + path).get().build(), path))
    }

    private suspend fun postObject(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(ApiConfig.BASE_URL + path)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        JSONObject(executeAndReadBody(request, path))
    }

    private fun executeAndReadBody(request: Request, path: String): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("${request.method} $path failed: HTTP ${response.code} — $body")
            }
            return body
        }
    }

    // ── JSON -> UI model mapping ────────────────────────────────────────────

    private fun parseBandedInt(json: JSONObject): Banded = Banded(
        floor = json.getDouble("floor").roundToInt(),
        policy = json.getDouble("policy").roundToInt(),
        mid = json.getDouble("mid").roundToInt(),
    )

    private fun parseBandedPrice(json: JSONObject): BandedPrice = BandedPrice(
        floor = json.getDouble("floor"),
        policy = json.getDouble("policy"),
        mid = json.getDouble("mid"),
    )

    private fun parseOptionRight(value: String): OptionRight = if (value == "CALL") OptionRight.CALL else OptionRight.PUT

    private fun parseAttribution(value: String?): Attribution? = when (value) {
        "theta" -> Attribution.THETA
        "vega" -> Attribution.VEGA
        "delta" -> Attribution.DELTA
        else -> null
    }

    private fun parseExitClock(json: JSONObject?): ExitClock? {
        if (json == null) return null
        val type = if (json.getString("type") == "expiry") ExitType.EXPIRY else ExitType.STOP
        return ExitClock(type, json.getInt("days"))
    }

    private fun parseAttributionSplit(json: JSONObject): AttributionSplit = AttributionSplit(
        driver = if (json.getString("driver") == "vega") AttributionDriver.VEGA else AttributionDriver.DELTA,
        deltaPct = json.getInt("delta_pct"),
        vegaPct = json.getInt("vega_pct"),
        note = json.getString("note"),
        vegaLabel = json.optString("vega_label", "IV"),
    )

    private fun parseOption(json: JSONObject): DecisionOption = DecisionOption(
        id = json.getString("id"),
        label = json.getString("label"),
        kind = when (json.getString("kind")) {
            "accept" -> OptionKind.ACCEPT
            "reject" -> OptionKind.REJECT
            "branch" -> OptionKind.BRANCH
            else -> OptionKind.DEFER
        },
        isDefault = json.optBoolean("is_default", false),
        requiresReason = json.optBoolean("requires_reason", false),
        detail = json.stringOrNull("detail"),
    )

    private fun parseOptions(json: JSONArray): List<DecisionOption> =
        (0 until json.length()).map { parseOption(json.getJSONObject(it)) }

    private fun parseStringArray(json: JSONArray): List<String> =
        (0 until json.length()).map { json.getString(it) }

    private fun parseDecisionCard(json: JSONObject): DecisionCard {
        val id = json.getString("id")
        val underlying = json.getString("underlying")
        val action = json.getString("action")
        val sub = json.getString("sub")
        val deadlineLabel = json.getString("deadline_label")
        val deadlineHot = json.optBoolean("deadline_hot", false)
        val options = parseOptions(json.getJSONArray("options"))
        return when (json.getString("type")) {
            "hold_or_exit" -> DecisionCard.VegaTrigger(
                id = id, underlying = underlying, action = action, sub = sub,
                deadlineLabel = deadlineLabel, deadlineHot = deadlineHot,
                split = parseAttributionSplit(json.getJSONObject("attribution_split")),
                reasons = parseStringArray(json.getJSONArray("reasons")),
                reasonsOpenByDefault = json.optBoolean("reasons_open_by_default", true),
                options = options,
            )
            "post_loss_branch" -> DecisionCard.PostLossBranch(
                id = id, underlying = underlying, action = action, sub = sub,
                deadlineLabel = deadlineLabel, deadlineHot = deadlineHot,
                split = parseAttributionSplit(json.getJSONObject("attribution_split")),
                options = options,
            )
            else -> DecisionCard.Entry(
                id = id, underlying = underlying, action = action, sub = sub,
                deadlineLabel = deadlineLabel, deadlineHot = deadlineHot,
                annualizedFloorPct = json.getDouble("annualized_floor_pct"),
                aprGatePct = json.optDouble("apr_gate_pct", 20.0),
                premium = parseBandedPrice(json.getJSONObject("premium")),
                ivRank = json.getInt("iv_rank"),
                assignmentOdds = json.getDouble("assignment_odds"),
                reasons = parseStringArray(json.getJSONArray("reasons")),
                reasonsOpenByDefault = json.optBoolean("reasons_open_by_default", false),
                options = options,
            )
        }
    }

    private fun parseBoardOptionLeg(json: JSONObject?): BoardOptionLeg? {
        if (json == null) return null
        return BoardOptionLeg(
            right = parseOptionRight(json.getString("right")),
            strike = json.getInt("strike"),
            expiry = json.getString("expiry"),
            qty = json.getInt("qty"),
            credit = json.getDouble("credit"),
            mark = parseBandedPrice(json.getJSONObject("mark")),
        )
    }

    private fun parseBoardStockLeg(json: JSONObject?): BoardStockLeg? {
        if (json == null) return null
        return BoardStockLeg(qty = json.getInt("qty"), basis = json.getDouble("basis"), last = json.getDouble("last"))
    }

    private fun parsePositionRow(json: JSONObject): PositionRow = PositionRow(
        ticker = json.getString("ticker"),
        stage = json.getInt("stage"),
        option = parseBoardOptionLeg(json.objOrNull("option")),
        stock = parseBoardStockLeg(json.objOrNull("stock")),
        distancePct = json.doubleOrNull("distance_pct"),
        attribution = parseAttribution(json.stringOrNull("attribution")),
        exit = parseExitClock(json.objOrNull("exit")),
    )

    /** Same source row as [parsePositionRow], projected onto the dashboard's narrower,
     *  non-nullable-attribution shape — null for a cash row (NVDA in the fixtures), same
     *  as the dashboard's compressed list has always only shown positions with open legs. */
    private fun parsePositionSummaryOrNull(json: JSONObject): PositionSummary? {
        val attribution = parseAttribution(json.stringOrNull("attribution")) ?: return null
        val exit = parseExitClock(json.objOrNull("exit")) ?: return null
        val optionJson = json.objOrNull("option")
        val option = optionJson?.let {
            OptionLeg(
                right = parseOptionRight(it.getString("right")),
                strike = it.getInt("strike"),
                expiry = it.getString("expiry"),
                qty = it.getInt("qty"),
                credit = it.getDouble("credit"),
                mark = parseBandedPrice(it.getJSONObject("mark")),
            )
        }
        val stockJson = json.objOrNull("stock")
        val stock = stockJson?.let { StockLeg(qty = it.getInt("qty"), basis = it.getDouble("basis"), last = it.getDouble("last")) }
        return PositionSummary(
            ticker = json.getString("ticker"), option = option, stock = stock,
            attribution = attribution, exit = exit,
        )
    }

    /** Non-null only for a ticker whose `detail` object the server actually sent (MSFT
     *  today — see api/mock-server/README.md). `band`/`syncedAgo`/`book` are left at
     *  their defaults; the caller applies the live values via `.copy(...)`. */
    private fun parsePositionDetailOrNull(json: JSONObject): PositionDetailState? {
        val detail = json.objOrNull("detail") ?: return null
        val option = json.getJSONObject("option")
        val iv = detail.getJSONObject("iv")
        val autoExit = detail.objOrNull("auto_exit")
        return PositionDetailState(
            ticker = json.getString("ticker"),
            right = parseOptionRight(option.getString("right")),
            strike = option.getInt("strike"),
            expiry = option.getString("expiry"),
            dte = option.getInt("dte"),
            qty = option.getInt("qty"),
            enteredDate = detail.getString("entered_date"),
            credit = option.getDouble("credit"),
            mark = parseBandedPrice(option.getJSONObject("mark")),
            stockPrice = detail.getDouble("stock_price"),
            distancePct = json.getDouble("distance_pct"),
            delta = detail.getDouble("delta"),
            marginConsumed = detail.getInt("margin_consumed"),
            assignmentOddsPct = detail.getInt("assignment_odds_pct"),
            iv = IvContext(atEntry = iv.getInt("at_entry"), now = iv.getInt("now")),
            attribution = parseAttributionSplit(detail.getJSONObject("attribution_split")),
            stopNote = detail.getString("stop_note"),
            autoExit = autoExit?.let { AutoExitNote(title = it.getString("title"), body = it.getString("body")) },
        )
    }
}

// ── org.json null-safety helpers — it predates Kotlin and treats "missing" and
// "JSON null" differently (has()/isNull()), so bare get()/opt() aren't enough. ──

private fun JSONObject.stringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONObject.doubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else getDouble(key)

private fun JSONObject.objOrNull(key: String): JSONObject? =
    if (!has(key) || isNull(key)) null else getJSONObject(key)
