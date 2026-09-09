package com.wheelhouse.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelhouse.app.ui.components.ScreenChrome
import com.wheelhouse.app.ui.components.WheelHouseBottomNav
import com.wheelhouse.app.ui.components.WheelHouseSection
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
import com.wheelhouse.app.ui.theme.Ok
import com.wheelhouse.app.ui.theme.Paper
import com.wheelhouse.app.ui.theme.Vega
import com.wheelhouse.app.ui.theme.VegaBg
import com.wheelhouse.app.ui.theme.VegaLine
import com.wheelhouse.app.ui.theme.VegaText
import com.wheelhouse.app.ui.theme.WarnBg
import com.wheelhouse.app.ui.theme.WarnInk
import com.wheelhouse.app.ui.theme.WarnLine
import com.wheelhouse.app.ui.theme.WheelHouseTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** The three fill assumptions every P&L crosses the seam as (CONTRACT.md "Band rule"). */
enum class PnlBand(val label: String) {
    FLOOR("floor"),
    POLICY("policy"),
    MID("mid"),
    ;

    /** Three values is few enough to cycle on tap; a fourth would want a menu. */
    fun next(): PnlBand = entries[(ordinal + 1) % entries.size]
}

/** A P&L-bearing value in all three bands, per the contract's band rule. */
data class Banded(val floor: Int, val policy: Int, val mid: Int) {
    operator fun get(band: PnlBand): Int = when (band) {
        PnlBand.FLOOR -> floor
        PnlBand.POLICY -> policy
        PnlBand.MID -> mid
    }
}

/** One column of dashboard question 2 — realized and unrealized are shown split. */
data class PnlWindow(val label: String, val realized: Banded, val unrealized: Banded)

data class Capital(val target: Int, val deployed: Int, val idle: Int)

/**
 * What drove an adverse mark. §6.1 requires vega-driven and delta-driven reds
 * be visibly unalike, so this is an encoding decision, not a label.
 */
enum class Attribution { THETA, VEGA, DELTA }

enum class ExitType(val shortLabel: String) { STOP("stop"), EXPIRY("expiry") }

data class ExitClock(val type: ExitType, val days: Int) {
    /** The wireframe escalates the clock chip inside three days. */
    val isSoon: Boolean get() = days <= 3
}

/** Prices carry a band the way P&L does — the close side of a position is a live quote. */
data class BandedPrice(val floor: Double, val policy: Double, val mid: Double) {
    operator fun get(band: PnlBand): Double = when (band) {
        PnlBand.FLOOR -> floor
        PnlBand.POLICY -> policy
        PnlBand.MID -> mid
    }
}

enum class OptionRight(val label: String) { PUT("PUT"), CALL("CALL") }

/**
 * The short option leg. [credit] is cash already received and carries no band; [mark] is what it
 * would cost to close now and is fully banded. That split is the band rule applied correctly, not
 * an exception to it — and [mark] is also the number the rubric's 2×-credit stop watches.
 */
data class OptionLeg(
    val right: OptionRight,
    val strike: Int,
    val expiry: String,
    val qty: Int,
    val credit: Double,
    val mark: BandedPrice,
)

/** The stock side of a covered-call state, which has its own economics. */
data class StockLeg(val qty: Int, val basis: Double, val last: Double)

data class PositionSummary(
    val ticker: String,
    val option: OptionLeg?,
    val stock: StockLeg? = null,
    val attribution: Attribution,
    val exit: ExitClock,
) {
    /**
     * Derived rather than stored, so the row's economics and its P&L cannot drift apart —
     * the drift that had the design doc's detail view claiming 88% of credit captured on a
     * position trading well above its credit.
     */
    fun pnl(band: PnlBand): Int {
        val fromOption = option?.let { (it.credit - it.mark[band]) * 100 * it.qty } ?: 0.0
        val fromShares = stock?.let { (it.last - it.basis) * it.qty } ?: 0.0
        return (fromOption + fromShares).roundToInt()
    }

    /** `$430 PUT · Jul 31` — what the position *is*, before any engine commentary. */
    val contractLabel: String?
        get() = option?.let {
            "$${it.strike} ${it.right.label} · ${it.expiry}" + if (it.qty > 1) " · ×${it.qty}" else ""
        }

    /** `sold $3.10 → now $6.50` — the gain/loss that matters on short premium. */
    fun economicsLabel(band: PnlBand): String? = option?.let {
        "sold ${premium(it.credit)} → now ${premium(it.mark[band])}" +
            (stock?.let { shares -> " · ${shares.qty} sh" } ?: "")
    }
}

/**
 * The stable semantic category CONTRACT.md's `options[]` carries — labels are the
 * engine's own words and free to vary; §2's veto-rate metric counts `REJECT` only.
 */
enum class OptionKind { ACCEPT, REJECT, DEFER, BRANCH }

/** One entry of a decision card's `options[]` (CONTRACT.md "Decision card shape"). */
data class DecisionOption(
    val id: String,
    val label: String,
    val kind: OptionKind,
    val isDefault: Boolean = false,
    val requiresReason: Boolean = false,
    /** Branch options only — the wireframe's `.branch-d` line under the radio. */
    val detail: String? = null,
)

enum class AttributionDriver { DELTA, VEGA }

/**
 * The delta-vs-vega split a vega-trigger or post-loss card carries so the operator can
 * tell "adverse but the thesis is intact" from "the stock broke" before choosing an option.
 */
data class AttributionSplit(
    val driver: AttributionDriver,
    val deltaPct: Int,
    val vegaPct: Int,
    val note: String,
    /** "IV expansion" on the vega-trigger card reads differently than plain "IV" on a branch card. */
    val vegaLabel: String = "IV",
) {
    val headlinePct: Int get() = if (driver == AttributionDriver.VEGA) vegaPct else deltaPct
}

/**
 * One §6.2 decision card. A sealed class rather than one flexible data class because the three
 * card shapes genuinely differ (headline APR + gate vs. attribution split vs. a branch list) —
 * CONTRACT.md's card shape varies with `type`, and Kotlin's `when` keeps each shape exhaustive
 * to render instead of nullable fields nobody remembers to check.
 */
sealed class DecisionCard {
    abstract val id: String
    abstract val underlying: String
    abstract val action: String
    abstract val sub: String
    abstract val deadlineLabel: String
    abstract val deadlineHot: Boolean
    abstract val options: List<DecisionOption>

    data class Entry(
        override val id: String,
        override val underlying: String,
        override val action: String,
        override val sub: String,
        override val deadlineLabel: String,
        override val deadlineHot: Boolean = false,
        val annualizedFloorPct: Double,
        val aprGatePct: Double = 20.0,
        val premium: BandedPrice,
        val ivRank: Int,
        val assignmentOdds: Double,
        val reasons: List<String>,
        val reasonsOpenByDefault: Boolean = false,
        override val options: List<DecisionOption>,
    ) : DecisionCard()

    /** The rubric's vega exception (RUBRIC.md) — a 2x-credit stop with no `is_default`, since the
     *  engine has no recommendation when the trigger and the attribution disagree. */
    data class VegaTrigger(
        override val id: String,
        override val underlying: String,
        override val action: String,
        override val sub: String,
        override val deadlineLabel: String,
        override val deadlineHot: Boolean = true,
        val split: AttributionSplit,
        val reasons: List<String>,
        val reasonsOpenByDefault: Boolean = true,
        override val options: List<DecisionOption>,
    ) : DecisionCard()

    data class PostLossBranch(
        override val id: String,
        override val underlying: String,
        override val action: String,
        override val sub: String,
        override val deadlineLabel: String,
        override val deadlineHot: Boolean = false,
        val split: AttributionSplit,
        override val options: List<DecisionOption>,
    ) : DecisionCard()
}

private val VegaExitOptions = listOf(
    DecisionOption("exit_now", "Exit now", OptionKind.ACCEPT),
    DecisionOption("hold", "Hold", OptionKind.REJECT, requiresReason = true),
    DecisionOption("snooze", "Snooze", OptionKind.DEFER),
)

private val AmdBranchOptions = listOf(
    DecisionOption(
        "reenter_aggressive", "Re-enter, more aggressive", OptionKind.BRANCH, isDefault = true,
        detail = "Sell a new put harvesting the post-drop IV, strike below the fresh 90-day low.",
    ),
    DecisionOption(
        "cooldown", "Cooldown the name", OptionKind.BRANCH,
        detail = "Bench AMD for the cooldown period. No new entries; existing legs still managed.",
    ),
    DecisionOption(
        "walk_away", "Walk away", OptionKind.BRANCH,
        detail = "Drop AMD from the active universe until manually re-added.",
    ),
)

/** wireframes.html `DATA.entry` / `vegaCard` / `branchCard` — same illustrative numbers. */
fun sampleDecisionCards(): List<DecisionCard> = listOf(
    DecisionCard.VegaTrigger(
        id = "d-2026-07-27-002",
        underlying = "MSFT",
        action = "MSFT \$430 put hit its 2× stop",
        sub = "Sold \$3.10 · now \$6.50 to close — 2.10× credit · 4 DTE",
        deadlineLabel = "41m",
        split = AttributionSplit(
            driver = AttributionDriver.VEGA, deltaPct = 22, vegaPct = 78, vegaLabel = "IV expansion",
            note = "IV rank 54 → 81 since entry, stock still 2.1% above strike. " +
                "Adverse mark, thesis intact — the rubric's vega exception.",
        ),
        reasons = listOf(
            "Stop-loss triggered at 2× credit — but attribution is vega-dominant",
            "Vega-driven triggers raise a card instead of auto-exiting (gate matrix)",
            "Recorded habit: these were held \"more aggressively or for longer than usual\"",
        ),
        options = VegaExitOptions,
    ),
    DecisionCard.Entry(
        id = "d-2026-07-27-003",
        underlying = "TSLA",
        action = "Sell 1 TSLA \$290 put",
        sub = "expires Aug 14 · 18 DTE",
        deadlineLabel = "2h 14m",
        annualizedFloorPct = 21.4,
        premium = BandedPrice(floor = 6.15, policy = 6.28, mid = 6.40),
        ivRank = 78,
        assignmentOdds = 0.24,
        reasons = listOf(
            "APR gate: 22.1% ≥ 20% target — (0.8 × premium) ÷ margin × 365/14",
            "Strike \$290 sits below the 90-day trailing low of \$296",
            "Price at 38th pct of 52wk range",
            "No earnings before expiry",
            "STAPLE tier · 1 contract · 2.9% of target capital",
        ),
        options = listOf(
            DecisionOption("approve", "Approve", OptionKind.ACCEPT, isDefault = true),
            DecisionOption("veto", "Veto", OptionKind.REJECT, requiresReason = true),
            DecisionOption("snooze", "Snooze", OptionKind.DEFER),
        ),
    ),
    DecisionCard.PostLossBranch(
        id = "d-2026-07-27-004",
        underlying = "AMD",
        action = "AMD closed at a loss — what next?",
        sub = "Exited \$170 put at 2× credit · realized −\$412 (floor)",
        deadlineLabel = "1d 04h",
        split = AttributionSplit(
            driver = AttributionDriver.DELTA, deltaPct = 81, vegaPct = 19,
            note = "The stock slid through the strike. Not a vega mark — the thesis moved against us.",
        ),
        options = AmdBranchOptions,
    ),
)

data class HomeDashboardState(
    val band: PnlBand = PnlBand.FLOOR,
    val syncedAgo: String = "42s",
    val book: String = "BASE",
    val nextCheck: String = "tomorrow 09:30",
    val windows: List<PnlWindow>,
    val capital: Capital,
    val positions: List<PositionSummary>,
    /** Deadline-sorted, per §6.2 — the sample list is already in that order. */
    val decisions: List<DecisionCard> = emptyList(),
)

private val UnrealizedToDate = Banded(floor = -320, policy = -278, mid = -255)

fun sampleHomeDashboardState() = HomeDashboardState(
    windows = listOf(
        PnlWindow("Today", Banded(214, 228, 239), UnrealizedToDate),
        PnlWindow("Week", Banded(686, 724, 751), UnrealizedToDate),
        PnlWindow("Month", Banded(2140, 2268, 2361), UnrealizedToDate),
        PnlWindow("Year", Banded(11480, 12190, 12704), UnrealizedToDate),
    ),
    capital = Capital(target = 200_000, deployed = 86_400, idle = 113_600),
    positions = listOf(
        PositionSummary(
            "TSLA",
            OptionLeg(OptionRight.PUT, 290, "Aug 14", 1, 6.15, BandedPrice(4.73, 4.47, 4.34)),
            attribution = Attribution.THETA, exit = ExitClock(ExitType.STOP, 10),
        ),
        PositionSummary(
            "MSFT",
            OptionLeg(OptionRight.PUT, 430, "Jul 31", 1, 3.10, BandedPrice(6.50, 6.15, 5.98)),
            attribution = Attribution.VEGA, exit = ExitClock(ExitType.STOP, 2),
        ),
        PositionSummary(
            "AMD",
            OptionLeg(OptionRight.CALL, 165, "Aug 7", 1, 2.85, BandedPrice(3.75, 3.51, 3.37)),
            stock = StockLeg(qty = 100, basis = 159.20, last = 158.00),
            attribution = Attribution.DELTA, exit = ExitClock(ExitType.EXPIRY, 11),
        ),
        PositionSummary(
            "GOOG",
            OptionLeg(OptionRight.PUT, 175, "Aug 21", 1, 2.10, BandedPrice(1.22, 1.13, 1.06)),
            attribution = Attribution.THETA, exit = ExitClock(ExitType.STOP, 11),
        ),
    ),
)

/** wireframes.html "Home dashboard — 3 pending" (§6.2) — the same book, cards attached. */
fun sampleHomeDashboardBusyState() = sampleHomeDashboardState().copy(decisions = sampleDecisionCards())

/**
 * wireframes.html "Home dashboard — quiet" (REQUIREMENTS v0.8 §6.2).
 *
 * Home is a dashboard answering four questions in order, not a decision queue.
 * When nothing is pending, question 1 collapses to a single strip and the space
 * it used to own goes to P&L and leverage — the reassurance is still there, it
 * just stops being the whole screen.
 */
@Composable
fun HomeDashboardScreen(
    state: HomeDashboardState = sampleHomeDashboardState(),
    paused: Boolean = false,
    /** No offline queuing of approvals (§6.2) — false shows the same offline-note the cards carry. */
    canAct: Boolean = true,
    onAllPositionsClick: () -> Unit = {},
    onNavSelect: (WheelHouseSection) -> Unit = {},
    /** Fixtures-only for now: wires the tap through, but doesn't round-trip. `reason` is non-null
     *  only for a `requires_reason` option — the veto sheet (wireframes.html `vetoSheet`) captures
     *  it before this fires. */
    onOptionSelected: (cardId: String, optionId: String, reason: String?) -> Unit = { _, _, _ -> },
) {
    // §1 wants band to be one app-wide mode, but there is only one screen so far. Holding it
    // here keeps the control honest today; it moves above the nav graph — and into DataStore,
    // since §6.8 makes it a settings default and the widget runs in its own process — when a
    // second surface needs to inherit it. state.band is the starting value.
    var band by rememberSaveable { mutableStateOf(state.band) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column(Modifier.background(Bg).statusBarsPadding()) {
                AppBar(title = "WheelHouse", band = band, onBandChange = { band = band.next() })
                ScreenChrome(syncedAgo = state.syncedAgo, book = state.book, paused = paused)
            }
        },
        bottomBar = {
            WheelHouseBottomNav(
                selected = WheelHouseSection.HOME,
                homePendingCount = state.decisions.size,
                onSelect = onNavSelect,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val pending = state.decisions
            if (pending.isEmpty()) {
                QuestionHeader(number = 1, title = "Decisions", isFirst = true)
                QuietStrip(nextCheck = state.nextCheck)
            } else {
                QuestionHeader(
                    number = 1,
                    title = "${pending.size} decisions need you",
                    isFirst = true,
                    linkLabel = "Deadline order",
                )
                pending.forEach { card ->
                    DecisionCardView(
                        card = card,
                        band = band,
                        canAct = canAct,
                        onOptionSelected = onOptionSelected,
                        modifier = Modifier.padding(bottom = 11.dp),
                    )
                }
            }

            QuestionHeader(number = 2, title = "What we've made")
            PnlBlock(windows = state.windows, band = band)

            QuestionHeader(number = 3, title = "How leveraged")
            LeverageBlock(capital = state.capital)

            QuestionHeader(
                number = 4,
                title = "Positions",
                linkLabel = "All ›",
                onLinkClick = onAllPositionsClick,
            )
            // Cards dominate question 1 when pending, so question 4 compresses to a taste —
            // "All ›" already exists for the rest (wireframes.html posQuick(3) vs posQuick()).
            val visiblePositions = if (pending.isEmpty()) state.positions else state.positions.take(3)
            visiblePositions.forEach { PositionQuickRow(position = it, band = band) }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppBar(title: String, band: PnlBand, onBandChange: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        BandChip(band, onBandChange)
    }
}

@Composable
private fun BandChip(band: PnlBand, onBandChange: () -> Unit) {
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

/** Section header carrying its dashboard question number. */
@Composable
private fun QuestionHeader(
    number: Int,
    title: String,
    isFirst: Boolean = false,
    linkLabel: String? = null,
    onLinkClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (isFirst) 2.dp else 18.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(Line2),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Ink2)
            }
            Text(
                title.uppercase(),
                modifier = Modifier.padding(start = 5.dp),
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = Ink3,
                fontWeight = FontWeight.Bold,
            )
        }
        if (linkLabel != null) {
            Text(
                linkLabel,
                fontSize = 11.sp,
                color = Ink3,
                modifier = Modifier.clickable(enabled = onLinkClick != null) { onLinkClick?.invoke() },
            )
        }
    }
}

/** Question 1's answer when nothing is pending — one line, not a hero. */
@Composable
private fun QuietStrip(nextCheck: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Fill)
            .border(1.dp, Line2, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(19.dp).clip(CircleShape).border(1.5.dp, Ok, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(9.dp)) {
                val tick = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.52f)
                    lineTo(size.width * 0.40f, size.height * 0.82f)
                    lineTo(size.width * 0.94f, size.height * 0.16f)
                }
                drawPath(
                    tick,
                    color = Ok,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Column(Modifier.padding(start = 9.dp)) {
            Text("Nothing needs you", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                "Next deadline-bearing check: $nextCheck",
                modifier = Modifier.padding(top = 1.dp),
                fontSize = 11.sp,
                color = Ink2,
                lineHeight = 15.sp,
            )
        }
    }
}

/** Question 2 — net per window, with the realized/unrealized split kept visible. */
@Composable
private fun PnlBlock(windows: List<PnlWindow>, band: PnlBand) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Line2, RoundedCornerShape(8.dp))
            .height(IntrinsicSize.Min),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            windows.forEachIndexed { index, window ->
                if (index > 0) VerticalHairline()
                PnlCell(window = window, band = band, modifier = Modifier.weight(1f))
            }
        }
    }
    Text(
        "Realized + unrealized · ${band.label} band",
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        fontSize = 10.sp,
        color = Ink3,
        textAlign = TextAlign.End,
    )
}

@Composable
private fun PnlCell(window: PnlWindow, band: PnlBand, modifier: Modifier = Modifier) {
    val realized = window.realized[band]
    val unrealized = window.unrealized[band]
    val net = realized + unrealized
    Column(
        modifier
            .fillMaxHeight()
            .background(Paper)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            window.label.uppercase(),
            fontSize = 9.sp,
            letterSpacing = 0.55.sp,
            color = Ink3,
        )
        Text(
            compactMoney(net),
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (net < 0) AlarmInk else Ink,
        )
        Text(
            "${compactMoney(realized)} real\n${compactMoney(unrealized)} unreal",
            modifier = Modifier.padding(top = 3.dp),
            fontSize = 8.5.sp,
            color = Ink3,
            lineHeight = 11.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Question 3. The note is load-bearing: it reconciles a ~21% per-position APR
 * with a portfolio-level bar, which otherwise reads as a contradiction.
 */
@Composable
private fun LeverageBlock(capital: Capital) {
    val deployedPct = (capital.deployed.toFloat() / capital.target * 100).roundToInt()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Line2, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Fill)
                .border(1.dp, Line2, RoundedCornerShape(5.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(deployedPct / 100f)
                    .fillMaxHeight()
                    .background(Color(0xFFDDD8D0)),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CapitalFigure("Deployed", capital.deployed, TextAlign.Start, Modifier.weight(1f))
            CapitalFigure("Idle", capital.idle, TextAlign.Center, Modifier.weight(1f))
            CapitalFigure("Target", capital.target, TextAlign.End, Modifier.weight(1f))
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = Line2, thickness = 1.dp)
        Text(
            "$deployedPct% deployed. Idle capital is why per-position APR (~21%) runs well " +
                "above portfolio return — the success bar is judged on the total.",
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 10.5.sp,
            color = Ink3,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun CapitalFigure(label: String, amount: Int, align: TextAlign, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 10.5.sp, color = Ink2, textAlign = align, modifier = Modifier.fillMaxWidth())
        Text(
            compactMoney(amount),
            modifier = Modifier.fillMaxWidth(),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            textAlign = align,
        )
    }
}

/**
 * Question 4 — contract first, then the engine's commentary. A row that opens with management
 * state asks the operator to trust the engine before it has said what is actually owned.
 */
@Composable
private fun PositionQuickRow(position: PositionSummary, band: PnlBand) {
    val value = position.pnl(band)
    val valueColor = when {
        value >= 0 -> Ink
        position.attribution == Attribution.VEGA -> Vega
        else -> AlarmInk
    }
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    Line2,
                    androidx.compose.ui.geometry.Offset(0f, size.height),
                    androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = 2.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(position.ticker, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                position.contractLabel?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(start = 6.dp),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink2,
                        maxLines = 1,
                    )
                }
                ExitClockChip(position.exit, Modifier.padding(start = 6.dp))
            }
            position.economicsLabel(band)?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 10.5.sp,
                    color = Ink3,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(signedMoney(value), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
            AttributionMark(position.attribution, Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun ExitClockChip(exit: ExitClock, modifier: Modifier = Modifier) {
    val background = if (exit.isSoon) WarnBg else Fill
    val outline = if (exit.isSoon) WarnLine else Line2
    val ink = if (exit.isSoon) WarnInk else Ink2
    Text(
        "${exit.type.shortLabel} ${exit.days}d",
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
 * Vega marks are drawn dashed and delta marks solid — the two-token vocabulary
 * §6.1 asks for, so an adverse-but-intact position never reads like a broken one.
 * Theta carries no mark on the dashboard; nothing is wrong, so nothing is said.
 */
@Composable
private fun AttributionMark(attribution: Attribution, modifier: Modifier = Modifier) {
    when (attribution) {
        Attribution.THETA -> Unit

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

@Composable
private fun VerticalHairline() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Line2))
}

/** Gains are signed on position rows — a bare `$142` reads as a balance, not a move. */
private fun signedMoney(amount: Int): String = if (amount > 0) "+" + money(amount) else money(amount)

private fun premium(value: Double): String = "$" + "%.2f".format(value)

private fun money(amount: Int): String {
    val grouped = abs(amount).toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (amount < 0) "−$" else "$") + grouped
}

/** Thousands collapse to one decimal — the dashboard grid has no room for full figures. */
private fun compactMoney(amount: Int): String {
    val magnitude = abs(amount)
    val body = if (magnitude >= 1000) "%.1fk".format(magnitude / 1000.0) else "$magnitude"
    return (if (amount < 0) "−$" else "$") + body
}

// ── Decision cards (§6.2 "3 pending") ──────────────────────────────────────────

@Composable
private fun DecisionCardView(
    card: DecisionCard,
    band: PnlBand,
    canAct: Boolean,
    onOptionSelected: (cardId: String, optionId: String, reason: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is DecisionCard.Entry -> EntryDecisionCard(card, band, canAct, onOptionSelected, modifier)
        is DecisionCard.VegaTrigger -> VegaDecisionCard(card, canAct, onOptionSelected, modifier)
        is DecisionCard.PostLossBranch -> BranchDecisionCard(card, canAct, onOptionSelected, modifier)
    }
}

@Composable
private fun CardShell(
    modifier: Modifier = Modifier,
    borderColor: Color = Line,
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Paper)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .padding(13.dp),
        content = content,
    )
}

@Composable
private fun CardHeader(
    typeLabel: String,
    action: String,
    sub: String,
    deadlineLabel: String,
    deadlineHot: Boolean,
    typeColor: Color = Ink3,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                typeLabel.uppercase(),
                fontSize = 9.5.sp,
                letterSpacing = 0.6.sp,
                color = typeColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                action,
                modifier = Modifier.padding(top = 3.dp),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                lineHeight = 19.sp,
            )
            Text(sub, modifier = Modifier.padding(top = 3.dp), fontSize = 10.5.sp, color = Ink3)
        }
        DeadlineChip(deadlineLabel, deadlineHot)
    }
}

@Composable
private fun DeadlineChip(label: String, hot: Boolean, modifier: Modifier = Modifier) {
    val background = if (hot) WarnBg else Fill
    val outline = if (hot) WarnLine else Line2
    val ink = if (hot) WarnInk else Ink2
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(background)
            .border(1.dp, outline, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = ink,
    )
}

/** The entry card's headline: annualized-at-floor, big, with the rubric gate check beside it. */
@Composable
private fun HeadlineNum(valueText: String, unitText: String, gateText: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(valueText, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                unitText,
                modifier = Modifier.padding(start = 7.dp),
                fontSize = 11.5.sp,
                color = Ink2,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                gateText,
                fontSize = 10.5.sp,
                color = Ok,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            )
        }
        HorizontalDivider(color = Line2, thickness = 1.dp)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun KvCell(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, fontSize = 9.5.sp, color = Ink3)
        Box(Modifier.padding(top = 2.dp)) { value() }
    }
}

@Composable
private fun EntryKvGrid(card: DecisionCard.Entry, band: PnlBand) {
    Row(Modifier.fillMaxWidth().padding(bottom = 11.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KvCell("PREMIUM", Modifier.weight(1f)) {
            Row {
                Text(premium(card.premium[band]), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(" ${band.label}", fontSize = 9.5.sp, color = Ink3)
            }
        }
        KvCell("IV RANK", Modifier.weight(1f)) {
            Text("${card.ivRank}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
        KvCell("ASSIGN ODDS", Modifier.weight(1f)) {
            Text(
                "${(card.assignmentOdds * 100).roundToInt()}%",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
        }
    }
}

/**
 * Collapsed by default except on the vega card, where the conflict is exactly why the
 * card exists — the wireframe opens it there and nowhere else.
 */
@Composable
private fun ReasonsDisclosure(reasons: List<String>, openByDefault: Boolean, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(openByDefault) }
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind { drawLine(Line2, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(top = 9.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = "Why the engine wants this (${reasons.size})"
                    role = Role.Button
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "▾" else "▸", fontSize = 9.sp, color = Ink3)
            Text(
                "Why the engine wants this (${reasons.size})",
                modifier = Modifier.padding(start = 5.dp),
                fontSize = 11.sp,
                color = Ink2,
            )
        }
        if (expanded) {
            Column(Modifier.padding(top = 9.dp)) {
                reasons.forEach {
                    Row(Modifier.padding(bottom = 4.dp)) {
                        Text("•  ", fontSize = 11.5.sp, color = Ink2)
                        Text(it, fontSize = 11.5.sp, color = Ink2, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

/**
 * The delta/vega split bar. Colour-coded to match the position row's own attribution
 * marks (AlarmLine for delta, Vega for vega) so the two surfaces read as one vocabulary.
 */
@Composable
private fun AttributionBlock(split: AttributionSplit, modifier: Modifier = Modifier) {
    val isVegaDominant = split.driver == AttributionDriver.VEGA
    val background = if (isVegaDominant) VegaBg else AlarmBg
    val border = if (isVegaDominant) VegaLine else AlarmLine
    val ink = if (isVegaDominant) VegaText else AlarmInk
    val headline = if (isVegaDominant) "vega ${split.vegaPct}%" else "delta ${split.deltaPct}%"
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("What moved the premium", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ink)
            Text(headline, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ink)
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
            Text("Δ delta ${split.deltaPct}%", fontSize = 9.5.sp, color = ink)
            Text("ν ${split.vegaLabel} ${split.vegaPct}%", fontSize = 9.5.sp, color = ink)
        }
        Text(split.note, modifier = Modifier.padding(top = 7.dp), fontSize = 11.sp, color = ink, lineHeight = 15.5.sp)
    }
}

private enum class OptionButtonStyle { PRIMARY, GHOST, PLAIN }

/** Mirrors wireframes.html `renderOptions`: shape comes from `is_default`/`kind`, not from card type. */
@Composable
private fun OptionsRow(
    options: List<DecisionOption>,
    canAct: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { option ->
            val style = when {
                option.isDefault -> OptionButtonStyle.PRIMARY
                option.kind == OptionKind.DEFER -> OptionButtonStyle.GHOST
                else -> OptionButtonStyle.PLAIN
            }
            OptionButton(
                option = option,
                style = style,
                enabled = canAct,
                modifier = if (style == OptionButtonStyle.GHOST) Modifier.width(78.dp) else Modifier.weight(1f),
                onClick = { onSelect(option.id) },
            )
        }
    }
}

/**
 * A Material button per style, not a styled `Text`.
 *
 * The custom version put `clickable(role = Role.Button)` on the `Text` itself, so one
 * semantics node carried both the text and the role — and the accessibility delegate
 * writes `className` in order, letting the text's `android.widget.TextView` clobber the
 * role's `android.widget.Button` (compose-ui 1.6.8, populateAccessibilityNodeInfoProperties).
 * TalkBack reads the role off `className`, so it announced the label and no role at all.
 *
 * Material's buttons keep the role on the container and the text on a child, which also
 * buys the 48dp minimum touch target (`minimumInteractiveComponentSize` inside
 * `Surface(onClick)`) that 12.5sp text plus 9dp padding fell ~13dp short of.
 */
@Composable
private fun OptionButton(
    option: DecisionOption,
    style: OptionButtonStyle,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ink = when (style) {
        OptionButtonStyle.PRIMARY -> Paper
        OptionButtonStyle.GHOST -> Ink2
        OptionButtonStyle.PLAIN -> Ink
    }
    val label: @Composable () -> Unit = {
        Text(
            option.label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
    // Disabled reads as a fade of the whole button, as before — but through Material's
    // disabled* colours rather than an `alpha` layer over the box, which is what blanked
    // the button on the enabled -> disabled -> enabled transition.
    when (style) {
        OptionButtonStyle.PRIMARY -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = OptionButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink,
                contentColor = ink,
                disabledContainerColor = Ink.copy(alpha = 0.4f),
                disabledContentColor = ink.copy(alpha = 0.7f),
            ),
            elevation = null,
            contentPadding = OptionButtonPadding,
            content = { label() },
        )
        OptionButtonStyle.GHOST, OptionButtonStyle.PLAIN -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = OptionButtonShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Paper,
                contentColor = ink,
                disabledContainerColor = Paper,
                disabledContentColor = ink.copy(alpha = 0.4f),
            ),
            border = BorderStroke(1.dp, if (enabled) Line else Line.copy(alpha = 0.4f)),
            contentPadding = OptionButtonPadding,
            content = { label() },
        )
    }
}

private val OptionButtonShape = RoundedCornerShape(7.dp)

/** Horizontal room for "Re-enter, more aggressive"; vertical is governed by Material's 40dp
 *  MinHeight and the 48dp touch target, so 9dp here only matters for taller labels. */
private val OptionButtonPadding = PaddingValues(horizontal = 6.dp, vertical = 9.dp)

/**
 * Branch cards pick a shape, not an accept/reject/defer verb. `is_default` renders as the
 * "engine's pick" label only — CONTRACT.md and the wireframe are both explicit that nothing
 * is pre-selected, so agreeing costs the same one tap as disagreeing.
 */
@Composable
private fun BranchOptionsList(
    options: List<DecisionOption>,
    canAct: Boolean,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth()) {
        options.forEach { option ->
            BranchOptionRow(
                option = option,
                selected = option.id == selectedId,
                modifier = Modifier
                    .padding(bottom = 7.dp)
                    .clickable(onClickLabel = option.label, role = Role.Button) { selectedId = option.id },
            )
        }
        OptionButton(
            option = DecisionOption("confirm", "Confirm choice", OptionKind.ACCEPT),
            style = OptionButtonStyle.PRIMARY,
            enabled = canAct && selectedId != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = { selectedId?.let(onConfirm) },
        )
        Text(
            "Nothing pre-selected — Confirm enables once you pick a branch.",
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            fontSize = 9.5.sp,
            color = Ink3,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BranchOptionRow(option: DecisionOption, selected: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Fill else Paper)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Ink else Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioGlyph(selected)
            Text(
                option.label,
                modifier = Modifier.padding(start = 7.dp).weight(1f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
            if (option.isDefault) {
                Text(
                    "ENGINE'S PICK",
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, Line, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    color = Ink3,
                )
            }
        }
        option.detail?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                fontSize = 10.5.sp,
                color = Ink2,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun RadioGlyph(selected: Boolean) {
    Box(
        Modifier.size(13.dp).clip(CircleShape).border(1.5.dp, if (selected) Ink else Ink3, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(Ink))
    }
}

/** No live connection ⇒ no approvals — §6.2's "no offline queuing" rule, spelled out in place. */
@Composable
private fun OfflineNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 9.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Fill)
            .border(1.dp, Line, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        fontSize = 11.sp,
        color = Ink2,
        lineHeight = 15.5.sp,
    )
}

/**
 * wireframes.html `vetoSheet()`. The sheet's own copy stays fixed to "veto" language
 * regardless of which button opened it (Veto, Hold, …) — the footnote on the vega card
 * makes the point explicit: "Hold" is `kind: reject`, it counts toward veto rate without
 * the word ever appearing on the button. This is where that word does appear, on purpose.
 */
@Composable
private fun VetoReasonSheetContent(
    reason: String,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp)) {
        Text("Why veto this?", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(
            "One line. Vetoes and their reasons feed proposed rubric diffs — this text is how " +
                "the strategy learns, so specifics beat categories.",
            modifier = Modifier.padding(top = 5.dp, bottom = 13.dp),
            fontSize = 11.5.sp,
            color = Ink2,
            lineHeight = 16.5.sp,
        )
        OutlinedTextField(
            value = reason,
            onValueChange = onReasonChange,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 68.dp),
            placeholder = {
                Text("What did the engine miss?", fontSize = 12.5.sp, color = Ink3)
            },
            textStyle = TextStyle(fontSize = 12.5.sp, color = Ink, lineHeight = 17.sp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink,
                unfocusedBorderColor = Ink,
                cursorColor = Ink,
                focusedContainerColor = Paper,
                unfocusedContainerColor = Paper,
            ),
        )
        Text(
            "Prompts, not buttons — what did the engine miss? · what would change your mind? · " +
                "is this the rule, or just this trade?",
            modifier = Modifier.padding(top = 9.dp, bottom = 14.dp),
            fontSize = 10.5.sp,
            color = Ink3,
            lineHeight = 15.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OptionButton(
                option = DecisionOption("confirm_veto", "Confirm veto", OptionKind.REJECT),
                style = OptionButtonStyle.PRIMARY,
                enabled = reason.isNotBlank(),
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
            )
            OptionButton(
                option = DecisionOption("cancel", "Cancel", OptionKind.DEFER),
                style = OptionButtonStyle.GHOST,
                enabled = true,
                modifier = Modifier.width(78.dp),
                onClick = onCancel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VetoReasonSheet(onDismiss: () -> Unit, onConfirm: (reason: String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Paper,
        dragHandle = { GrabberHandle() },
    ) {
        VetoReasonSheetContent(
            reason = reason,
            onReasonChange = { reason = it },
            onConfirm = { onConfirm(reason) },
            onCancel = onDismiss,
        )
    }
}

@Composable
private fun GrabberHandle() {
    Box(
        Modifier
            .padding(top = 8.dp, bottom = 14.dp)
            .width(34.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Line),
    )
}

/**
 * [OptionsRow], but a tap on a `requires_reason` option opens the veto sheet first — the
 * callback only fires once a reason is captured, never for a bare tap. Shared by the entry
 * card's Veto and the vega card's Hold, the two `kind: reject` options in the sample data.
 */
@Composable
private fun ReasonGatedOptionsRow(
    cardId: String,
    options: List<DecisionOption>,
    canAct: Boolean,
    onOptionSelected: (cardId: String, optionId: String, reason: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reasonSheetOptionId by rememberSaveable { mutableStateOf<String?>(null) }
    OptionsRow(
        options,
        canAct,
        modifier = modifier,
        onSelect = { optionId ->
            if (options.first { it.id == optionId }.requiresReason) {
                reasonSheetOptionId = optionId
            } else {
                onOptionSelected(cardId, optionId, null)
            }
        },
    )
    reasonSheetOptionId?.let { optionId ->
        VetoReasonSheet(
            onDismiss = { reasonSheetOptionId = null },
            onConfirm = { reason ->
                onOptionSelected(cardId, optionId, reason)
                reasonSheetOptionId = null
            },
        )
    }
}

@Composable
private fun EntryDecisionCard(
    card: DecisionCard.Entry,
    band: PnlBand,
    canAct: Boolean,
    onOptionSelected: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShell(modifier) {
        CardHeader(
            typeLabel = "open put",
            action = card.action,
            sub = card.sub,
            deadlineLabel = card.deadlineLabel,
            deadlineHot = card.deadlineHot,
        )
        Spacer(Modifier.height(9.dp))
        HeadlineNum(
            valueText = "%.1f%%".format(card.annualizedFloorPct),
            unitText = "annualized\nat floor",
            gateText = "✓ clears\n${card.aprGatePct.roundToInt()}% gate",
        )
        EntryKvGrid(card, band)
        ReasonsDisclosure(card.reasons, card.reasonsOpenByDefault, modifier = Modifier.padding(bottom = 11.dp))
        ReasonGatedOptionsRow(card.id, card.options, canAct, onOptionSelected)
        if (!canAct) {
            OfflineNote(
                "Approvals need a live connection. Approve round-trips to the engine — it can't " +
                    "be queued, because this deadline may pass before you reconnect.",
            )
        }
    }
}

@Composable
private fun VegaDecisionCard(
    card: DecisionCard.VegaTrigger,
    canAct: Boolean,
    onOptionSelected: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShell(modifier, borderColor = Vega, borderWidth = 1.5.dp) {
        CardHeader(
            typeLabel = "hold or exit · vega-driven",
            action = card.action,
            sub = card.sub,
            deadlineLabel = card.deadlineLabel,
            deadlineHot = card.deadlineHot,
            typeColor = Vega,
        )
        Spacer(Modifier.height(9.dp))
        AttributionBlock(card.split, modifier = Modifier.padding(bottom = 10.dp))
        ReasonsDisclosure(card.reasons, card.reasonsOpenByDefault, modifier = Modifier.padding(bottom = 11.dp))
        ReasonGatedOptionsRow(card.id, card.options, canAct, onOptionSelected)
        if (!canAct) {
            OfflineNote("Approvals need a live connection. This card escalates to a pager push before its deadline expires.")
        }
    }
}

/**
 * wireframes.html `vegaFrame()` — the vega-trigger card standalone, the target of a
 * DECISION push's deep link (§6.6): the operator taps the notification and lands on
 * just this card, not the whole dashboard. No band chip — RiskScreen sets the same
 * precedent for a screen with nothing banded on it; this card's dollar figures are
 * baked into `sub`, not sourced from a Banded value.
 */
@Composable
fun VegaTriggerCardScreen(
    card: DecisionCard.VegaTrigger = sampleDecisionCards().filterIsInstance<DecisionCard.VegaTrigger>().first(),
    pendingCount: Int = 3,
    canAct: Boolean = true,
    syncedAgo: String = "42s",
    book: String = "BASE",
    paused: Boolean = false,
    onOptionSelected: (cardId: String, optionId: String, reason: String?) -> Unit = { _, _, _ -> },
    onNavSelect: (WheelHouseSection) -> Unit = {},
) {
    Scaffold(
        containerColor = Bg,
        topBar = {
            Column(Modifier.background(Bg).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("WheelHouse", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                ScreenChrome(syncedAgo = syncedAgo, book = book, paused = paused)
            }
        },
        bottomBar = {
            WheelHouseBottomNav(
                selected = WheelHouseSection.HOME,
                homePendingCount = pendingCount,
                onSelect = onNavSelect,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            VegaDecisionCard(card, canAct, onOptionSelected)
        }
    }
}

@Composable
private fun BranchDecisionCard(
    card: DecisionCard.PostLossBranch,
    canAct: Boolean,
    onOptionSelected: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardShell(modifier) {
        CardHeader(
            typeLabel = "post-loss branch",
            action = card.action,
            sub = card.sub,
            deadlineLabel = card.deadlineLabel,
            deadlineHot = card.deadlineHot,
        )
        Spacer(Modifier.height(9.dp))
        AttributionBlock(card.split, modifier = Modifier.padding(bottom = 10.dp))
        // No branch option ever requires_reason (CONTRACT.md) — a plain OptionsRow-adjacent
        // list, not ReasonGatedOptionsRow; the veto sheet has nothing to do here.
        BranchOptionsList(card.options, canAct, onConfirm = { onOptionSelected(card.id, it, null) })
    }
}

/**
 * wireframes.html `branchFrame()` — the post-loss branch card standalone, the same
 * deep-link target pattern as [VegaTriggerCardScreen] (§6.6): the operator taps a
 * DECISION push and lands on just this card. No band chip, same reasoning as that
 * screen — this card carries no banded value either.
 */
@Composable
fun PostLossBranchCardScreen(
    card: DecisionCard.PostLossBranch = sampleDecisionCards().filterIsInstance<DecisionCard.PostLossBranch>().first(),
    pendingCount: Int = 3,
    canAct: Boolean = true,
    syncedAgo: String = "42s",
    book: String = "BASE",
    paused: Boolean = false,
    onOptionSelected: (cardId: String, optionId: String, reason: String?) -> Unit = { _, _, _ -> },
    onNavSelect: (WheelHouseSection) -> Unit = {},
) {
    Scaffold(
        containerColor = Bg,
        topBar = {
            Column(Modifier.background(Bg).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("WheelHouse", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                ScreenChrome(syncedAgo = syncedAgo, book = book, paused = paused)
            }
        },
        bottomBar = {
            WheelHouseBottomNav(
                selected = WheelHouseSection.HOME,
                homePendingCount = pendingCount,
                onSelect = onNavSelect,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            BranchDecisionCard(card, canAct, onOptionSelected)
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun HomeDashboardScreenPreview() {
    WheelHouseTheme {
        HomeDashboardScreen()
    }
}

/** wireframes.html "Home dashboard — 3 pending" — cards dominate question 1 (§6.2). */
@Preview(showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun HomeDashboardScreenBusyPreview() {
    WheelHouseTheme {
        HomeDashboardScreen(state = sampleHomeDashboardBusyState())
    }
}

/** Same three cards, offline — approvals disabled, offline-note shown on entry/vega. */
@Preview(showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun HomeDashboardScreenBusyOfflinePreview() {
    WheelHouseTheme {
        HomeDashboardScreen(state = sampleHomeDashboardBusyState(), canAct = false)
    }
}

/**
 * wireframes.html `vetoSheet()`'s content. Previewed on its own, outside `ModalBottomSheet` —
 * a `Popup`-backed composable doesn't render reliably in the static preview renderer, so the
 * presentational content is split out ([VetoReasonSheetContent]) precisely so it can be
 * checked here; the live sheet itself is verified on-device instead.
 */
@Preview(showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun VetoReasonSheetContentEmptyPreview() {
    WheelHouseTheme {
        VetoReasonSheetContent(reason = "", onReasonChange = {}, onConfirm = {}, onCancel = {})
    }
}

/** Confirm veto enables once there's a reason to send. */
@Preview(showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun VetoReasonSheetContentFilledPreview() {
    WheelHouseTheme {
        VetoReasonSheetContent(
            reason = "IV pop looks like the Aug 12 earnings date moving inside expiry, not a real vol bid",
            onReasonChange = {},
            onConfirm = {},
            onCancel = {},
        )
    }
}

/** wireframes.html `vegaFrame()` — a DECISION push's deep-link target (§6.6). */
@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun VegaTriggerCardScreenPreview() {
    WheelHouseTheme {
        VegaTriggerCardScreen()
    }
}

/** wireframes.html `branchFrame()` — the same deep-link pattern for a post-loss branch. */
@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun PostLossBranchCardScreenPreview() {
    WheelHouseTheme {
        PostLossBranchCardScreen()
    }
}

/** No offline-note on the branch card itself (wireframes.html omits it there too), but
 *  Confirm still respects canAct — nothing round-trips without a live connection. */
@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun PostLossBranchCardScreenOfflinePreview() {
    WheelHouseTheme {
        PostLossBranchCardScreen(canAct = false)
    }
}

/** Escalated past its deadline into a pager push (§6.6) — offline, so it can't be worked yet. */
@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun VegaTriggerCardScreenOfflinePreview() {
    WheelHouseTheme {
        VegaTriggerCardScreen(canAct = false)
    }
}
