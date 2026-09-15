package com.wheelhouse.app.ui.theme

import androidx.compose.ui.graphics.Color

// Palette lifted 1:1 from android/design/wireframes.html — greyscale plus the
// three state tints (warn/alarm/ok). Deliberately unstyled beyond that; see
// the wireframe's own "what's deliberately unresolved" note on colour.
val Ink = Color(0xFF1A1A1A)
val Ink2 = Color(0xFF5C5C5C)
val Ink3 = Color(0xFF8E8E8E)
val Line = Color(0xFFD8D5D0)
val Line2 = Color(0xFFEBE8E3)
val Paper = Color(0xFFFFFFFF)
val Bg = Color(0xFFF2F0EC)
val Fill = Color(0xFFF7F5F2)

val WarnBg = Color(0xFFFDF3E0)
val WarnLine = Color(0xFFE0B768)
val WarnInk = Color(0xFF8A5F14)

val AlarmBg = Color(0xFFFDECEB)
val AlarmLine = Color(0xFFDC9A95)
val AlarmInk = Color(0xFF96322B)

val Ok = Color(0xFF4A7C59)

// Vega-driven adverse marks. Distinct from Alarm on purpose: REQUIREMENTS §6.1
// wants "adverse mark, thesis intact" to read differently at a glance from
// "the stock moved against us".
val Vega = Color(0xFF5B6F9C)
val VegaBg = Color(0xFFEEF1F7)

// The vega attribution block (decision cards) uses a softer border/ink pair than
// the Vega chip elsewhere — wireframes.html's .attr.v, kept as its own tokens
// rather than reusing Vega/VegaBg so the two contexts can drift independently.
val VegaLine = Color(0xFFC6CFE0)
val VegaText = Color(0xFF3D4D70)
