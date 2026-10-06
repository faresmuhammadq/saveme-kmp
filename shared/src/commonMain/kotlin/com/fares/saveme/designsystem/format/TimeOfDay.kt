package com.fares.saveme.designsystem.format

/** A wall-clock time as `HH:MM`, 24-hour, Western digits in every language (P-06.2). [minutes] is minutes after midnight. */
fun formatTimeOfDay(minutes: Int): String {
    val clamped = minutes.coerceIn(0, 24 * 60 - 1)
    return "${(clamped / 60).toString().padStart(2, '0')}:${(clamped % 60).toString().padStart(2, '0')}"
}
