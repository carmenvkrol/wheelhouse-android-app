package com.wheelhouse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheelhouse.app.ui.theme.Fill
import com.wheelhouse.app.ui.theme.Ink2
import com.wheelhouse.app.ui.theme.Ink3
import com.wheelhouse.app.ui.theme.Line
import com.wheelhouse.app.ui.theme.Line2

/**
 * The per-screen chrome strip under the app bar (wireframes.html `chrome()`):
 * paused banner when the kill switch is engaged, then the freshness line.
 * Paused is global chrome, not risk-screen state — a paused engine shown only
 * where the switch lives is the same species of bug as silently-stale
 * positions (design/ui-from-requirements.md §1).
 */
@Composable
fun ScreenChrome(syncedAgo: String, book: String, paused: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)) {
        if (paused) PausedBanner(Modifier.padding(bottom = 6.dp))
        Text(
            "Synced $syncedAgo ago · $book book",
            fontSize = 11.sp,
            color = Ink3,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        HorizontalDivider(color = Line2, thickness = 1.dp)
    }
}

/** Quiet by design — paused is a chosen state, not a fault, so it isn't warn-tinted. */
@Composable
private fun PausedBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(Fill)
            .border(1.dp, Line, RoundedCornerShape(7.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.padding(top = 4.dp, end = 8.dp).size(7.dp).clip(CircleShape).background(Ink3),
        )
        Text(
            buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append("New entries paused.")
                }
                append(" The engine is still managing exits.")
            },
            fontSize = 11.5.sp,
            lineHeight = 16.5.sp,
            color = Ink2,
        )
    }
}
