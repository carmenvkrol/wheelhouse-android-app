package com.wheelhouse.app.ui.positions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelhouse.app.ui.components.ScreenChrome
import com.wheelhouse.app.ui.components.WheelHouseBottomNav
import com.wheelhouse.app.ui.components.WheelHouseSection
import com.wheelhouse.app.ui.home.Attribution
import com.wheelhouse.app.ui.home.BandedPrice
import com.wheelhouse.app.ui.home.ExitClock
import com.wheelhouse.app.ui.home.ExitType
import com.wheelhouse.app.ui.home.OptionRight
import com.wheelhouse.app.ui.home.PnlBand
import com.wheelhouse.app.ui.theme.AlarmBg
import com.wheelhouse.app.ui.theme.AlarmInk
import com.wheelhouse.app.ui.theme.AlarmLine
import com.wheelhouse.app.ui.theme.Bg
import com.wheelhouse.app.ui.theme.Fill
import com.wheelhouse.app.ui.theme.Ink
import com.wheelhouse.app.ui.theme.Ink2
import com.wheelhouse.app.ui.theme.Ink3
import com.wheelhouse.app.ui.theme.Line
import com.wheelhouse.app.ui.theme.Line2
import com.wheelhouse.app.ui.theme.Paper
import com.wheelhouse.app.ui.theme.Vega
import com.wheelhouse.app.ui.theme.VegaBg
import com.wheelhouse.app.ui.theme.WarnInk
import com.wheelhouse.app.ui.theme.WarnLine
import com.wheelhouse.app.ui.theme.WheelHouseTheme
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One open option leg on a board row. [credit] is cash already received and carries no
 * band; [mark] is the cost to close now and is fully banded — the split the contract
 * requires, and also the number the rubric's 2×-credit stop watches ([stopMultiple]).
 */
data class BoardOptionLeg(
    val right: OptionRight,
    val strike: Int,
    val expiry: String,
    val qty: Int,
    val credit: Double,
    val mark: BandedPrice,
) {
    fun stopMultiple(band: PnlBand): Double = mark[band] / credit

    val contractLabel: String
        get() = "$${strike} ${right.label} · $expiry" + if (qty > 1) " · ×$qty" else ""
}

/** The stock side of a covered-call position. [last] is a live quote, not banded. */
data class BoardStockLeg(val qty: Int, val basis: Double, val last: Double)

/**
 * One §6.1 board row. `stage` is the wheel's 1..4 progress — RUBRIC.md owns what each
 * stage means; this board only draws how far along it is. `distancePct` is null exactly
 * when there's no open option leg to be near a strike of (the cash-only sample row).
 */
data class PositionRow(
    val ticker: String,
    val stage: Int,
    val option: BoardOptionLeg?,
    val stock: BoardStockLeg? = null,
    val distancePct: Double? = null,
    val attribution: Attribution? = null,
    val exit: ExitClock? = null,
) {
    fun optionPnl(band: PnlBand): Double =
        option?.let { (it.credit - it.mark[band]) * 100 * it.qty } ?: 0.0

    fun stockPnl(): Double = stock?.let { (it.last - it.basis) * it.qty } ?: 0.0

    fun totalPnl(band: PnlBand): Double = optionPnl(band) + stockPnl()

    /** wireframes.html's `x.lab==="cash" ? "cash" : x.shares ? "shares + call" : "short "+right`. */
    val stateLabel: String
        get() = when {
            option == null -> "cash"
            stock != null -> "shares + call"
            else -> "short ${option.right.label.lowercase()}"
        }
}

data class Aggregates(
    val marginUtilizationPct: Int,
    val netExposure: Int,
    val netExposureMeterPct: Int,
    val syntheticCash: Int,
    val syntheticCashMeterPct: Int,
)

data class PositionsBoardState(
    val band: PnlBand = PnlBand.FLOOR,
    val syncedAgo: String = "42s",
    val book: String = "BASE",
    val aggregates: Aggregates,
    val rows: List<PositionRow>,
) {
    fun total(band: PnlBand): Double = rows.sumOf { it.totalPnl(band) }
}

fun samplePositionsBoardState() = PositionsBoardState(
    aggregates = Aggregates(
        marginUtilizationPct = 62,
        netExposure = 86_400,
        netExposureMeterPct = 77,
        syntheticCash = 41_200,
        syntheticCashMeterPct = 38,
    ),
    rows = listOf(
        PositionRow(
            ticker = "TSLA",
            stage = 2,
            option = BoardOptionLeg(OptionRight.PUT, 290, "Aug 14", 1, 6.15, BandedPrice(4.73, 4.47, 4.34)),
            distancePct = 7.6,
            attribution = Attribution.THETA,
            exit = ExitClock(ExitType.STOP, 10),
        ),
        PositionRow(
            ticker = "MSFT",
            stage = 2,
            option = BoardOptionLeg(OptionRight.PUT, 430, "Jul 31", 1, 3.10, BandedPrice(6.50, 6.15, 5.98)),
            distancePct = -2.1,
            attribution = Attribution.VEGA,
            exit = ExitClock(ExitType.STOP, 2),
        ),
        PositionRow(
            ticker = "AMD",
            stage = 3,
            option = BoardOptionLeg(OptionRight.CALL, 165, "Aug 7", 1, 2.85, BandedPrice(3.75, 3.51, 3.37)),
            stock = BoardStockLeg(qty = 100, basis = 159.20, last = 158.00),
            distancePct = -4.2,
            attribution = Attribution.DELTA,
            exit = ExitClock(ExitType.EXPIRY, 11),
        ),
        PositionRow(
            ticker = "GOOG",
            stage = 2,
            option = BoardOptionLeg(OptionRight.PUT, 175, "Aug 21", 1, 2.10, BandedPrice(1.22, 1.13, 1.06)),
            distancePct = 3.4,
            attribution = Attribution.THETA,
            exit = ExitClock(ExitType.STOP, 11),
        ),
        PositionRow(
            ticker = "NVDA",
            stage = 1,
            option = null,
        ),
    ),
)

/**
 * wireframes.html `positions()` (REQUIREMENTS §6.1) — every open position, contract
 * identity and economics first, the engine's management of it second. Contract-first
 * ordering matters: a row that opens with wheel state asks the operator to trust the
 * engine before it has said what is actually owned (same reasoning as the dashboard's
 * quick row, `HomeDashboardScreen.kt`).
 */
@Composable
fun PositionsScreen(
    state: PositionsBoardState = samplePositionsBoardState(),
    paused: Boolean = false,
    onNavSelect: (WheelHouseSection) -> Unit = {},
    /** wireframes.html's `detail()` drill-down only models one ticker's fixture (§6.1's
     *  [PositionDetailScreen]) — callers decide which tickers actually navigate. */
    onRowClick: (ticker: String) -> Unit = {},
) {
    var band by rememberSaveable { mutableStateOf(state.band) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column(Modifier.background(Bg).statusBarsPadding()) {
                PositionsAppBar(band = band, onBandChange = { band = band.next() })
                ScreenChrome(syncedAgo = state.syncedAgo, book = state.book, paused = paused)
            }
        },
        bottomBar = {
            WheelHouseBottomNav(selected = WheelHouseSection.POSITIONS, onSelect = onNavSelect)
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            AggregatesRow(state.aggregates)

            BoardSectionHeader(
                underlyingCount = state.rows.size,
                total = state.total(band),
                band = band,
            )
            state.rows.forEach {
                PositionBoardRow(row = it, band = band, onClick = { onRowClick(it.ticker) })
            }

            Text(
                "Credit received is a fact and carries no band. Cost to close, and every P&L " +
                    "above, is the ${band.label} value.",
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                fontSize = 10.sp,
                color = Ink3,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PositionsAppBar(band: PnlBand, onBandChange: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("Positions", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClickLabel = "Change which P&L band is shown", role = Role.Button) {
                    onBandChange()
                }
                .background(Fill)
                .border(1.dp, Line, RoundedCornerShape(20.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Text("P&L: ", fontSize = 10.5.sp, color = Ink2)
            Text(band.label, fontSize = 10.5.sp, color = Ink, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** wireframes.html `.agg` — margin utilization, net exposure, synthetic cash (§6.1 aggregates). */
@Composable
private fun AggregatesRow(aggregates: Aggregates) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Line2, RoundedCornerShape(8.dp)),
    ) {
        AggCell("Margin", "${aggregates.marginUtilizationPct}%", aggregates.marginUtilizationPct, false, Modifier.weight(1f))
        AggVerticalHairline()
        AggCell("Net exposure", compactMoney(aggregates.netExposure), aggregates.netExposureMeterPct, true, Modifier.weight(1f))
        AggVerticalHairline()
        AggCell("Syn. cash", compactMoney(aggregates.syntheticCash), aggregates.syntheticCashMeterPct, false, Modifier.weight(1f))
    }
}

@Composable
private fun AggCell(label: String, value: String, meterPct: Int, hot: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.background(Paper).padding(10.dp)) {
        Text(label, fontSize = 10.5.sp, color = Ink3)
        Text(
            value,
            modifier = Modifier.padding(top = 3.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Line2),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(meterPct / 100f)
                    .fillMaxHeight()
                    .background(if (hot) WarnLine else Ink2),
            )
        }
    }
}

@Composable
private fun AggVerticalHairline() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Line2))
}

/** wireframes.html `.section-h` with the total-P&L variant: label left, banded total right. */
@Composable
private fun BoardSectionHeader(underlyingCount: Int, total: Double, band: PnlBand) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "$underlyingCount UNDERLYINGS",
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = Ink3,
            fontWeight = FontWeight.Bold,
        )
        Row {
            Text("Total ${money(total.roundToInt())} ", fontSize = 11.sp, color = Ink)
            Text("(${band.label})", fontSize = 11.sp, color = Ink3)
        }
    }
}

@Composable
private fun PositionBoardRow(row: PositionRow, band: PnlBand, onClick: () -> Unit) {
    val value = row.totalPnl(band)
    val valueColor = when {
        row.option == null -> Ink3
        value >= 0 -> Ink
        row.attribution == Attribution.VEGA -> Vega
        else -> AlarmInk
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open ${row.ticker} detail", role = Role.Button, onClick = onClick)
            .drawBehind {
                drawLine(
                    Line2,
                    androidx.compose.ui.geometry.Offset(0f, size.height),
                    androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(vertical = 10.dp),
    ) {
        // Line 1 — contract identity, and the row's total P&L.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f, fill = false)) {
                Text(row.ticker, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                if (row.option != null) {
                    Text(
                        row.option.contractLabel,
                        modifier = Modifier.padding(start = 6.dp),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink2,
                    )
                } else {
                    Text(
                        "no open contracts",
                        modifier = Modifier.padding(start = 6.dp),
                        fontSize = 11.5.sp,
                        color = Ink3,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                }
            }
            Text(
                if (row.option != null) signedMoney(value.roundToInt()) else "—",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
        }

        // Line 1b — economics: option-only rows explain the stop; two-leg rows break out
        // each leg's own P&L, since line 1's total would otherwise read as one contract's.
        row.option?.let { option ->
            if (row.stock != null) {
                EconomicsLine(
                    label = option.right.label.lowercase(),
                    text = "sold ${premium(option.credit)} → now ${premium(option.mark[band])}",
                    multiple = option.stopMultiple(band),
                    attribution = row.attribution,
                    trailingValue = signedMoney(row.optionPnl(band).roundToInt()),
                    trailingColor = legColor(row.optionPnl(band), row.attribution),
                )
                EconomicsLine(
                    label = "${row.stock.qty} sh",
                    text = "@ ${premium(row.stock.basis)} → ${premium(row.stock.last)}",
                    multiple = null,
                    attribution = null,
                    trailingValue = signedMoney(row.stockPnl().roundToInt()),
                    trailingColor = if (row.stockPnl() < 0) AlarmInk else Ink,
                )
            } else {
                EconomicsLine(
                    label = null,
                    text = "sold ${premium(option.credit)} → now ${premium(option.mark[band])}",
                    multiple = option.stopMultiple(band),
                    attribution = row.attribution,
                    trailingValue = null,
                    trailingLabel = if (option.stopMultiple(band) >= 2) "past the 2× stop" else "of the 2× stop",
                )
            }
        }

        // Line 2 — how the engine is managing it.
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelDots(stage = row.stage)
            Text(
                row.stateLabel.uppercase(),
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
                color = Ink3,
                fontWeight = FontWeight.Bold,
            )
            row.exit?.let {
                ExitClockChip(it, Modifier.padding(start = 8.dp))
            }
            row.distancePct?.let {
                Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DistanceBar(it)
                    Text(
                        "${if (it > 0) "+" else ""}${it}%",
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 10.sp,
                        color = Ink3,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            AttributionMark(row.attribution)
        }
    }
}

@Composable
private fun EconomicsLine(
    label: String?,
    text: String,
    multiple: Double?,
    attribution: Attribution?,
    trailingValue: String?,
    trailingColor: Color = Ink,
    trailingLabel: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            label?.let {
                Text(it, fontSize = 11.sp, color = Ink3)
                Text(" ", fontSize = 11.sp, color = Ink3)
            }
            Text(text, fontSize = 11.sp, color = Ink2, fontWeight = FontWeight.Medium)
            multiple?.let {
                val color = stopMultipleColor(it, attribution)
                Text(
                    " · %.2f×".format(it),
                    fontSize = 11.sp,
                    color = color ?: Ink2,
                    fontWeight = if (color != null) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
        when {
            trailingValue != null -> Text(
                trailingValue,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = trailingColor,
            )
            trailingLabel != null -> Text(trailingLabel, fontSize = 11.sp, color = Ink3)
        }
    }
}

/** The rubric's 2×-credit stop: past it is alarm/vega-colored and bold, near it is a warning. */
private fun stopMultipleColor(multiple: Double, attribution: Attribution?): Color? = when {
    multiple >= 2.0 -> if (attribution == Attribution.VEGA) Vega else AlarmInk
    multiple >= 1.5 -> WarnInk
    else -> null
}

private fun legColor(pnl: Double, attribution: Attribution?): Color = when {
    pnl >= 0 -> Ink
    attribution == Attribution.VEGA -> Vega
    else -> AlarmInk
}

@Composable
private fun WheelDots(stage: Int) {
    Row {
        for (n in 1..4) {
            Box(
                Modifier
                    .padding(end = 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (n <= stage) Ink else Line),
            )
        }
    }
}

@Composable
private fun ExitClockChip(exit: ExitClock, modifier: Modifier = Modifier) {
    val background = if (exit.isSoon) com.wheelhouse.app.ui.theme.WarnBg else Fill
    val outline = if (exit.isSoon) WarnLine else Line2
    val ink = if (exit.isSoon) WarnInk else Ink2
    Text(
        "${if (exit.type == ExitType.STOP) "stop" else "exp"} ${exit.days}d",
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(background)
            .border(1.dp, outline, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = ink,
    )
}

/**
 * A diverging bar from center: left for a strike price below (negative distance), right
 * for above. Saturates at ±3.4% (wireframes.html's `min(abs(dist)/10*50,50)` clipped
 * against the 34px container — the same math, kept in dp).
 */
@Composable
private fun DistanceBar(distancePct: Double) {
    val halfWidth = 17.dp
    val filled = min(abs(distancePct) * 5.0, 17.0).dp
    val color = when {
        distancePct < -3 -> AlarmLine
        distancePct < 0 -> WarnLine
        else -> Ink3
    }
    Row(
        Modifier
            .width(34.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(Line2),
    ) {
        Box(Modifier.width(halfWidth).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            if (distancePct < 0) Box(Modifier.width(filled).fillMaxHeight().background(color))
        }
        Box(Modifier.width(halfWidth).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            if (distancePct >= 0) Box(Modifier.width(filled).fillMaxHeight().background(color))
        }
    }
}

/**
 * Vega marks are dashed, delta marks solid — the same two-token vocabulary as the
 * dashboard's quick row (`HomeDashboardScreen.kt`'s `AttributionMark`). Unlike that
 * compressed row, theta gets its own plain chip here rather than staying silent — the
 * full board has the room, and cash rows (attribution null) show nothing at all.
 */
@Composable
private fun AttributionMark(attribution: Attribution?, modifier: Modifier = Modifier) {
    when (attribution) {
        null -> Unit

        Attribution.THETA -> Text(
            "theta",
            modifier = modifier
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, Line2, RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink3,
        )

        Attribution.DELTA -> Text(
            "Δ-driven",
            modifier = modifier
                .clip(RoundedCornerShape(3.dp))
                .background(AlarmBg)
                .border(1.dp, AlarmLine, RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = AlarmInk,
        )

        Attribution.VEGA -> Text(
            "vega",
            modifier = modifier
                .clip(RoundedCornerShape(3.dp))
                .background(VegaBg)
                .dashedBorder(Vega, cornerRadius = 3.dp)
                .padding(horizontal = 5.dp, vertical = 1.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Vega,
        )
    }
}

private fun Modifier.dashedBorder(color: Color, cornerRadius: androidx.compose.ui.unit.Dp) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
        ),
    )
}

/** Gains are signed on position rows — a bare `$142` reads as a balance, not a move. */
private fun signedMoney(amount: Int): String = if (amount > 0) "+" + money(amount) else money(amount)

private fun premium(value: Double): String = "$" + "%.2f".format(value)

private fun money(amount: Int): String {
    val grouped = abs(amount).toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (amount < 0) "−$" else "$") + grouped
}

private fun compactMoney(amount: Int): String {
    val magnitude = abs(amount)
    val body = if (magnitude >= 1000) "%.1fk".format(magnitude / 1000.0) else "$magnitude"
    return (if (amount < 0) "−$" else "$") + body
}

@Preview(showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun PositionsScreenPreview() {
    WheelHouseTheme {
        PositionsScreen()
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun PositionsScreenPausedPreview() {
    WheelHouseTheme {
        PositionsScreen(paused = true)
    }
}
