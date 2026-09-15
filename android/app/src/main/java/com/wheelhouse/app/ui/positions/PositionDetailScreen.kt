package com.wheelhouse.app.ui.positions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelhouse.app.ui.components.ScreenChrome
import com.wheelhouse.app.ui.components.WheelHouseBottomNav
import com.wheelhouse.app.ui.components.WheelHouseSection
import com.wheelhouse.app.ui.home.AttributionDriver
import com.wheelhouse.app.ui.home.AttributionSplit
import com.wheelhouse.app.ui.home.BandedPrice
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
import com.wheelhouse.app.ui.theme.VegaLine
import com.wheelhouse.app.ui.theme.WarnBg
import com.wheelhouse.app.ui.theme.WarnInk
import com.wheelhouse.app.ui.theme.WarnLine
import com.wheelhouse.app.ui.theme.WheelHouseTheme
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** IV rank at entry and now — §6.1's "per-position IV context". */
data class IvContext(val atEntry: Int, val now: Int) {
    val rankDelta: Int get() = now - atEntry
}

/** A note about the day-14 stop / expiry race, only shown when one is imminent. */
data class AutoExitNote(val title: String, val body: String)

/**
 * §6.1's per-position drill-down (wireframes.html `detail()`). Scoped to a single short
 * option leg — the one case the wireframe models — not the covered-call two-leg shape
 * the board row also draws; that drill-down is a future extension, not invented here.
 */
data class PositionDetailState(
    val ticker: String,
    val right: OptionRight,
    val strike: Int,
    val expiry: String,
    val dte: Int,
    val qty: Int,
    val enteredDate: String,
    val credit: Double,
    val mark: BandedPrice,
    val stockPrice: Double,
    val distancePct: Double,
    val delta: Double,
    val marginConsumed: Int,
    val assignmentOddsPct: Int,
    val iv: IvContext,
    val attribution: AttributionSplit,
    val stopNote: String,
    val autoExit: AutoExitNote?,
    val band: PnlBand = PnlBand.FLOOR,
    val syncedAgo: String = "42s",
    val book: String = "BASE",
) {
    fun stopMultiple(band: PnlBand): Double = mark[band] / credit
    fun creditReceived(): Double = credit * 100 * qty
    fun costToClose(band: PnlBand): Double = mark[band] * 100 * qty
    fun unrealized(band: PnlBand): Double = creditReceived() - costToClose(band)
}

fun samplePositionDetailState() = PositionDetailState(
    ticker = "MSFT",
    right = OptionRight.PUT,
    strike = 430,
    expiry = "Jul 31",
    dte = 4,
    qty = 1,
    enteredDate = "Jul 15",
    credit = 3.10,
    mark = BandedPrice(6.50, 6.15, 5.98),
    stockPrice = 421.0,
    distancePct = -2.1,
    delta = -0.52,
    marginConsumed = 6_900,
    assignmentOddsPct = 52,
    iv = IvContext(atEntry = 54, now = 81),
    attribution = AttributionSplit(
        driver = AttributionDriver.VEGA, deltaPct = 22, vegaPct = 78, vegaLabel = "theta + vega",
        note = "The mark is adverse because options got expensive, not because the stock broke. " +
            "It sits 2.1% above strike — the thesis is intact.",
    ),
    stopNote = "This is the number the 2× stop watches — it has already crossed. Attribution is " +
        "vega-dominant, so the gate matrix raised a decision card instead of selling.",
    autoExit = AutoExitNote(
        title = "Day-14 stop in 2 days",
        body = "Fires Jul 29, ahead of the Jul 31 expiry — but the 2× stop above got there first, " +
            "and its card is open.",
    ),
)

/**
 * wireframes.html `detail()` (§6.1). Every number the board row summarizes, unpacked:
 * the stop meter against its gate, the auto-exit race, IV context, and the attribution
 * split that explains why the mark moved. Every value is either the fact/banded split
 * CONTRACT.md requires (credit is a fact; cost to close, unrealized, and the stop
 * multiple are banded) or explicitly unbanded (stock price, delta, margin, odds).
 *
 * The stop meter's fill, the ×credit label, "Cost to close", and "Unrealized" all share
 * one escalation color ([stopMultipleColor]) rather than the wireframe's flat vega tint —
 * the same plain → warn(1.5×) → alarm/vega(2×) scale [PositionsScreen] uses on the board
 * row, so a healthy position's detail screen doesn't read as pre-alarmed. Distance-to-
 * strike is likewise threshold-colored rather than fixed alarm-red, for the same reason.
 */
@Composable
fun PositionDetailScreen(
    state: PositionDetailState = samplePositionDetailState(),
    paused: Boolean = false,
    onBack: () -> Unit = {},
    onNavSelect: (WheelHouseSection) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var band by rememberSaveable { mutableStateOf(state.band) }
    val escalation = stopMultipleColor(state.stopMultiple(band), state.attribution.driver)

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column(Modifier.background(Bg).statusBarsPadding()) {
                DetailAppBar(title = state.ticker, band = band, onBandChange = { band = band.next() })
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
            DetailHeader(state)

            DetailSectionHeader("Economics")
            EconomicsGrid(state, band, escalation)
            StopMeter(state, band, escalation)
            UnrealizedRow(state, band, escalation)
            Text(
                state.stopNote,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                fontSize = 11.sp,
                color = Ink3,
                lineHeight = 16.sp,
            )

            state.autoExit?.let {
                DetailSectionHeader("Auto-exit")
                AutoExitBox(it)
            }

            DetailSectionHeader("IV context")
            IvRow(state.iv)

            DetailSectionHeader("P&L attribution", trailingLabel = money(state.unrealized(band).roundToInt()), trailingBand = band)
            AttributionBlock(state.attribution)

            DetailSectionHeader("Position")
            DetailRow("Stock price", premium(state.stockPrice), Ink, isLast = false)
            DetailRow(
                "Distance to strike",
                "${if (state.distancePct > 0) "+" else ""}${state.distancePct}%",
                distanceColor(state.distancePct),
                isLast = false,
            )
            DetailRow("Delta", "${state.delta}", Ink, isLast = false)
            DetailRow("Margin consumed", money(state.marginConsumed), Ink, isLast = false)
            DetailRow("Assignment odds", "${state.assignmentOddsPct}%", Ink, isLast = true)

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailAppBar(title: String, band: PnlBand, onBandChange: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
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

@Composable
private fun DetailHeader(state: PositionDetailState) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .drawBehind {
                drawLine(Line2, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(state.ticker, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                " short ${state.right.label.lowercase()}",
                modifier = Modifier.padding(bottom = 2.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Ink3,
            )
        }
        Text(
            "$${state.strike} ${state.right.label} · expires ${state.expiry} · ${state.dte} DTE · " +
                "×${state.qty} · entered ${state.enteredDate}",
            modifier = Modifier.padding(top = 3.dp),
            fontSize = 11.5.sp,
            color = Ink2,
        )
    }
}

@Composable
private fun DetailSectionHeader(label: String, trailingLabel: String? = null, trailingBand: PnlBand? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = Ink3,
            fontWeight = FontWeight.Bold,
        )
        if (trailingLabel != null) {
            Row {
                Text("$trailingLabel ", fontSize = 11.sp, color = Ink)
                trailingBand?.let { Text("(${it.label})", fontSize = 11.sp, color = Ink3) }
            }
        }
    }
}

@Composable
private fun EconomicsGrid(state: PositionDetailState, band: PnlBand, escalation: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Line2, RoundedCornerShape(8.dp))
            .height(IntrinsicSize.Min),
    ) {
        EconCell("CREDIT RECEIVED", modifier = Modifier.weight(1f)) {
            Text(money(state.creditReceived().roundToInt()), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                "${state.qty} × ${premium(state.credit)} at open · a fact, no band",
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 10.sp,
                color = Ink3,
            )
        }
        EconVerticalHairline()
        EconCell("COST TO CLOSE", modifier = Modifier.weight(1f)) {
            Text(money(state.costToClose(band).roundToInt()), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = escalation)
            Text(
                "${state.qty} × ${premium(state.mark[band])} · ${band.label} band",
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 10.sp,
                color = Ink3,
            )
        }
    }
}

@Composable
private fun EconCell(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxHeight().background(Paper).padding(horizontal = 11.dp, vertical = 10.dp)) {
        Text(label, fontSize = 9.5.sp, color = Ink3, letterSpacing = 0.4.sp)
        Column(Modifier.padding(top = 3.dp), content = content)
    }
}

@Composable
private fun EconVerticalHairline() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Line2))
}

/** wireframes.html `.stopmeter` — fill saturates at 2.5× credit; the gate sits at a fixed 80% (2 ÷ 2.5). */
@Composable
private fun StopMeter(state: PositionDetailState, band: PnlBand, escalation: Color) {
    val multiple = state.stopMultiple(band)
    val fillFraction = min(multiple / 2.5, 1.0).toFloat()
    val gateFraction = 2f / 2.5f
    Column(Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 4.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Line2),
        ) {
            Box(Modifier.fillMaxWidth(fillFraction).fillMaxHeight().background(escalation))
            Box(
                Modifier
                    .offset(x = maxWidth * gateFraction - 0.75.dp)
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(Ink),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("%.2f× credit".format(multiple), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = escalation)
            Text("2× stop", fontSize = 10.sp, color = Ink3)
        }
    }
}

@Composable
private fun UnrealizedRow(state: PositionDetailState, band: PnlBand, escalation: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(Line2, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .padding(vertical = 9.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Unrealized", fontSize = 12.sp, color = Ink2)
        Row {
            Text(signedMoney(state.unrealized(band).roundToInt()), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = escalation)
            Text(" (${band.label})", fontSize = 12.sp, color = Ink3)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color, isLast: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.sp, color = Ink2)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
    if (!isLast) HorizontalDivider(color = Line2, thickness = 1.dp)
}

@Composable
private fun AutoExitBox(note: AutoExitNote) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(WarnBg)
            .border(1.dp, WarnLine, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("⏱", fontSize = 18.sp)
        Column(Modifier.padding(start = 9.dp)) {
            Text(note.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WarnInk)
            Text(note.body, modifier = Modifier.padding(top = 1.dp), fontSize = 11.sp, color = WarnInk, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun IvRow(iv: IvContext) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Line2, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IvBlock("At entry", "${iv.atEntry}", Ink, null, Modifier.weight(1f))
        Text("→", fontSize = 15.sp, color = Ink3)
        IvBlock("Now", "${iv.now}", Vega, "+${iv.rankDelta} rank", Modifier.weight(1f))
    }
}

@Composable
private fun IvBlock(label: String, value: String, valueColor: Color, delta: String?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontSize = 9.5.sp, color = Ink3, letterSpacing = 0.4.sp)
        Text(value, modifier = Modifier.padding(top = 2.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        delta?.let {
            Text(it, modifier = Modifier.padding(top = 1.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Vega)
        }
    }
}

/** wireframes.html's `.attr.v` block, generalized to either driver via [AttributionSplit]. */
@Composable
private fun AttributionBlock(split: AttributionSplit) {
    val isVega = split.driver == AttributionDriver.VEGA
    val tint = if (isVega) Vega else AlarmInk
    val bg = if (isVega) VegaBg else AlarmBg
    val border = if (isVega) VegaLine else AlarmLine
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (isVega) "Vega-driven" else "Delta-driven", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
            Text("${if (isVega) split.vegaPct else split.deltaPct}% of the move", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Paper),
        ) {
            Box(Modifier.weight(split.deltaPct.coerceAtLeast(1).toFloat()).fillMaxHeight().background(AlarmLine))
            Box(Modifier.weight(split.vegaPct.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Vega))
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Δ delta ${split.deltaPct}%", fontSize = 9.5.sp, color = tint)
            Text("ν ${split.vegaLabel} ${split.vegaPct}%", fontSize = 9.5.sp, color = tint)
        }
        Text(split.note, modifier = Modifier.padding(top = 7.dp), fontSize = 11.sp, color = tint, lineHeight = 15.5.sp)
    }
}

/** Same escalation scale as [PositionsScreen]'s board row: plain → warn(1.5×) → alarm/vega(2×). */
private fun stopMultipleColor(multiple: Double, driver: AttributionDriver): Color = when {
    multiple >= 2.0 -> if (driver == AttributionDriver.VEGA) Vega else AlarmInk
    multiple >= 1.5 -> WarnInk
    else -> Ink
}

private fun distanceColor(distancePct: Double): Color = when {
    distancePct < -3 -> AlarmInk
    distancePct < 0 -> WarnInk
    else -> Ink
}

private fun signedMoney(amount: Int): String = if (amount > 0) "+" + money(amount) else money(amount)

private fun premium(value: Double): String = "$" + "%.2f".format(value)

private fun money(amount: Int): String {
    val grouped = abs(amount).toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (amount < 0) "−$" else "$") + grouped
}

@Preview(showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun PositionDetailScreenPreview() {
    WheelHouseTheme {
        PositionDetailScreen()
    }
}

/** A healthy position — no auto-exit urgency, delta-driven, well inside the stop. */
@Preview(showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun PositionDetailScreenHealthyPreview() {
    WheelHouseTheme {
        PositionDetailScreen(
            state = samplePositionDetailState().copy(
                mark = BandedPrice(1.22, 1.13, 1.06),
                distancePct = 3.4,
                autoExit = null,
                stopNote = "Well inside the 2× stop and the strike — theta is doing its job.",
                attribution = samplePositionDetailState().attribution.copy(
                    driver = AttributionDriver.DELTA, deltaPct = 81, vegaPct = 19,
                    note = "Price drift, not a vol event.",
                ),
            ),
        )
    }
}
