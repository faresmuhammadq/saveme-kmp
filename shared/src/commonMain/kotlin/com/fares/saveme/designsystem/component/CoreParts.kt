package com.fares.saveme.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.fares.saveme.designsystem.money.MoneyFormatter

/** Reports [by] less height than the content measures, centring the content: a 48dp touch target that sits like a 32dp line of text. */
fun Modifier.trimHeight(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val trim = by.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - trim) { placeable.place(0, -trim / 2) }
}

/** A whole-unit figure without the currency: "9,700", or "9,700.50" when it has a fraction. */
fun plainAmount(minor: Long, currency: String): String {
    val digits = MoneyFormatter.digitsFor(currency)
    var unit = 1L
    repeat(digits) { unit *= 10 }
    return if (minor % unit == 0L) MoneyFormatter.number(minor / unit, 0) else MoneyFormatter.number(minor, digits)
}

/** [plainAmount] with a leading sign: a true minus for outgoing, a plus for incoming, nothing for zero. */
fun signedPlainAmount(minor: Long, currency: String): String = when {
    minor < 0 -> "−" + plainAmount(-minor, currency)
    minor > 0 -> "+" + plainAmount(minor, currency)
    else -> plainAmount(0, currency)
}
