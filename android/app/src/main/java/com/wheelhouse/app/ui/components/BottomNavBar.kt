package com.wheelhouse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelhouse.app.ui.theme.Ink
import com.wheelhouse.app.ui.theme.Ink3
import com.wheelhouse.app.ui.theme.Line2
import com.wheelhouse.app.ui.theme.Paper

/**
 * Journal and Backtests aren't here (yet). The wireframe draws all five nav
 * slots, but neither has a wireframe frame of its own or a built screen —
 * showing a tab for a screen that doesn't exist yet reads as a promise the
 * app can't keep. Add each section back here, with its screen, when its
 * turn comes; MainActivity's nav switch has no other unbuilt-tab gating to
 * update since the nav only ever renders what's in this enum.
 */
enum class WheelHouseSection(val label: String) {
    HOME("Home"),
    POSITIONS("Positions"),
    RISK("Risk"),
}

/**
 * Global chrome — the same slots on every screen (wireframes.html `.nav`,
 * trimmed to built sections only — see [WheelHouseSection]). The wireframe
 * deliberately leaves the glyph unresolved (an empty bordered square, no
 * icon) — see design/ui-from-requirements.md §2. Kept faithful to that
 * rather than inventing icon design that wasn't asked for.
 */
@Composable
fun WheelHouseBottomNav(
    selected: WheelHouseSection,
    modifier: Modifier = Modifier,
    /** §6.2's "pending count exposed for badge/widget use" — Home only, per the wireframe. */
    homePendingCount: Int = 0,
    onSelect: (WheelHouseSection) -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Paper)
            .drawBehind {
                drawLine(Line2, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .navigationBarsPadding(),
    ) {
        WheelHouseSection.entries.forEach { section ->
            NavSlot(
                section = section,
                isSelected = section == selected,
                badgeCount = if (section == WheelHouseSection.HOME) homePendingCount else 0,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(section) },
            )
        }
    }
}

@Composable
private fun NavSlot(
    section: WheelHouseSection,
    isSelected: Boolean,
    badgeCount: Int,
    modifier: Modifier = Modifier,
) {
    val tint = if (isSelected) Ink else Ink3
    Column(
        modifier
            .drawBehind {
                if (isSelected) {
                    drawLine(Ink, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
                }
            }
            .padding(top = 9.dp, bottom = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(17.dp)
                .border(1.5.dp, tint, RoundedCornerShape(4.dp))
                .then(if (isSelected) Modifier.background(Ink, RoundedCornerShape(4.dp)) else Modifier),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                section.label,
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 9.5.sp,
                color = tint,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (badgeCount > 0) {
                Text(
                    "$badgeCount",
                    modifier = Modifier
                        .padding(top = 4.dp, start = 3.dp)
                        .defaultMinSize(minWidth = 15.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ink)
                        .padding(horizontal = 4.dp),
                    fontSize = 9.sp,
                    lineHeight = 15.sp,
                    color = Paper,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
