package com.wheelhouse.app.ui.risk

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelhouse.app.ui.components.ScreenChrome
import com.wheelhouse.app.ui.components.WheelHouseBottomNav
import com.wheelhouse.app.ui.components.WheelHouseSection
import com.wheelhouse.app.ui.theme.AlarmInk
import com.wheelhouse.app.ui.theme.Bg
import com.wheelhouse.app.ui.theme.Fill
import com.wheelhouse.app.ui.theme.Ink
import com.wheelhouse.app.ui.theme.Ink2
import com.wheelhouse.app.ui.theme.Ink3
import com.wheelhouse.app.ui.theme.Line
import com.wheelhouse.app.ui.theme.Line2
import com.wheelhouse.app.ui.theme.Ok
import com.wheelhouse.app.ui.theme.Paper
import com.wheelhouse.app.ui.theme.WheelHouseTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** One `/v1/risk` concentration entry. Sector concentration waits on the contract naming it. */
data class ConcentrationSlice(val underlying: String, val pct: Int)

data class RiskState(
    val assignAllCost: Int,
    val cashMarginAvailable: Int,
    val concentration: List<ConcentrationSlice>,
    val syncedAgo: String = "42s",
    val book: String = "BASE",
) {
    /** How much of the coverable capacity an assign-everything day would consume. */
    val capacityUsedPct: Int get() = (assignAllCost.toFloat() / cashMarginAvailable * 100).roundToInt()
    val isCovered: Boolean get() = assignAllCost <= cashMarginAvailable

    /** "Other" is a remainder bucket, not a name, so it can't be the largest single name. */
    val largestName: ConcentrationSlice?
        get() = concentration.filterNot { it.underlying.equals("Other", ignoreCase = true) }
            .maxByOrNull { it.pct }
}

fun sampleRiskState() = RiskState(
    assignAllCost = 86_400,
    cashMarginAvailable = 112_000,
    concentration = listOf(
        ConcentrationSlice("TSLA", 31),
        ConcentrationSlice("MSFT", 24),
        ConcentrationSlice("GOOG", 18),
        ConcentrationSlice("AMD", 15),
        ConcentrationSlice("Other", 12),
    ),
)

/**
 * wireframes.html "Risk + kill switch" (REQUIREMENTS §6.4).
 *
 * One dominant comparison — assign-all vs. available — because it's one relationship,
 * not a table. Concentration is posed as a question rather than reported, and the kill
 * switch copy says what pausing does NOT stop (design/ui-from-requirements.md §6).
 *
 * No band chip here: `/v1/risk` carries no P&L-bearing values, so there is nothing on
 * this surface for the global band mode to label.
 *
 * [toggleInFlight] exists because the kill switch round-trips through the API
 * (POST /v1/pause | /v1/resume) — the button must have an honest in-between state,
 * same rule as approvals.
 */
@Composable
fun RiskScreen(
    state: RiskState = sampleRiskState(),
    paused: Boolean = false,
    toggleInFlight: Boolean = false,
    onToggleEntries: () -> Unit = {},
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
                    Text("Risk", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                ScreenChrome(syncedAgo = state.syncedAgo, book = state.book, paused = paused)
            }
        },
        bottomBar = {
            WheelHouseBottomNav(selected = WheelHouseSection.RISK, onSelect = onNavSelect)
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
            AssignAllHero(state)

            SectionLabel("Concentration by underlying")
            state.concentration.forEach {
                ConcentrationRow(slice = it, maxPct = state.concentration.maxOf { s -> s.pct })
            }
            state.largestName?.let { ConcentrationQuestion(it) }

            SectionLabel("Kill switch")
            KillSwitchBox(paused = paused, inFlight = toggleInFlight, onToggle = onToggleEntries)

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The §6.4 headline: if everything assigns at once, can the book cover it? */
@Composable
private fun AssignAllHero(state: RiskState) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .padding(15.dp),
    ) {
        Text(
            "If every open position assigned tomorrow, against what's available to cover it.",
            fontSize = 11.sp,
            color = Ink2,
            lineHeight = 16.sp,
        )
        Box(
            Modifier
                .padding(top = 11.dp)
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Fill)
                .border(1.dp, Line2, RoundedCornerShape(6.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((state.capacityUsedPct / 100f).coerceAtMost(1f))
                    .fillMaxHeight()
                    .background(if (state.isCovered) Color(0xFFDDD8D0) else AlarmInk.copy(alpha = 0.35f)),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HeroFigure("Assign-all cost", state.assignAllCost, TextAlign.Start, Modifier.weight(1f))
            HeroFigure("Cash + margin", state.cashMarginAvailable, TextAlign.End, Modifier.weight(1f))
        }
        HorizontalDivider(Modifier.padding(top = 11.dp), color = Line2, thickness = 1.dp)
        Text(
            if (state.isCovered) {
                "✓ Covered — ${state.capacityUsedPct}% of available capacity"
            } else {
                "✕ Not covered — assign-all exceeds capacity (${state.capacityUsedPct}%)"
            },
            modifier = Modifier.padding(top = 11.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (state.isCovered) Ok else AlarmInk,
        )
    }
}

@Composable
private fun HeroFigure(label: String, amount: Int, align: TextAlign, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Ink2, textAlign = align, modifier = Modifier.fillMaxWidth())
        Text(
            money(amount),
            modifier = Modifier.fillMaxWidth(),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            textAlign = align,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(top = 18.dp, bottom = 9.dp),
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = Ink3,
        fontWeight = FontWeight.Bold,
    )
}

/** Bars are scaled to the largest slice, so the top name always reads full-width. */
@Composable
private fun ConcentrationRow(slice: ConcentrationSlice, maxPct: Int) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            slice.underlying,
            modifier = Modifier.width(42.dp),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        Box(
            Modifier
                .padding(start = 9.dp)
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Line2),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((slice.pct.toFloat() / maxPct).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Ink2),
            )
        }
        Text(
            "${slice.pct}%",
            modifier = Modifier.width(41.dp),
            fontSize = 11.sp,
            color = Ink2,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Posed as a question, not a report: no per-name ceiling exists yet, and whether one
 * should is exactly the concentration-vs-yield tradeoff the instrument measures.
 */
@Composable
private fun ConcentrationQuestion(largest: ConcentrationSlice) {
    Text(
        "Largest single name: ${largest.underlying} at ${largest.pct}%. No per-name ceiling " +
            "is set — the rubric flags this as an open parameter and the " +
            "concentration-vs-yield sweep is meant to answer it.",
        modifier = Modifier.padding(top = 2.dp),
        fontSize = 11.sp,
        color = Ink3,
        lineHeight = 16.5.sp,
    )
}

/**
 * The copy must say pausing stops entries, not exits — otherwise "paused" reads as
 * "everything stopped" and the switch becomes scarier than the thing it guards against.
 */
@Composable
private fun KillSwitchBox(paused: Boolean, inFlight: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Text(
            if (paused) "New entries are paused" else "New entries active",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        Text(
            buildAnnotatedString {
                append("Pausing stops the engine opening new positions. It ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Ink)) {
                    append("keeps managing exits")
                }
                append(" — rolls, buy-backs and assignments continue. Reversible at any time.")
            },
            modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
            fontSize = 11.5.sp,
            color = Ink2,
            lineHeight = 17.sp,
        )
        KillSwitchButton(paused = paused, inFlight = inFlight, onToggle = onToggle)
    }
}

@Composable
private fun KillSwitchButton(paused: Boolean, inFlight: Boolean, onToggle: () -> Unit) {
    val label = when {
        inFlight && paused -> "Resuming…"
        inFlight -> "Pausing…"
        paused -> "Resume new entries"
        else -> "Pause new entries"
    }
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (paused) Ink else Paper)
            .border(1.5.dp, if (inFlight) Ink3 else Ink, RoundedCornerShape(8.dp))
            .clickable(
                enabled = !inFlight,
                onClickLabel = if (paused) "Resume new entries" else "Pause new entries",
                role = Role.Button,
                onClick = onToggle,
            )
            .padding(vertical = 11.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (paused) Paper else Ink,
        textAlign = TextAlign.Center,
    )
}

private fun money(amount: Int): String {
    val grouped = abs(amount).toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (amount < 0) "−$" else "$") + grouped
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun RiskScreenPreview() {
    WheelHouseTheme { RiskScreen() }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun RiskScreenPausedPreview() {
    WheelHouseTheme { RiskScreen(paused = true) }
}

/** The drawdown-day fixture's stressed state — assign-all exceeding capacity. */
@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun RiskScreenStressedPreview() {
    WheelHouseTheme {
        RiskScreen(
            state = sampleRiskState().copy(assignAllCost = 131_800, cashMarginAvailable = 112_000),
        )
    }
}
