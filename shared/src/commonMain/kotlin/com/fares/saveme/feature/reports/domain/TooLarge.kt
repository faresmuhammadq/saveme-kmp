package com.fares.saveme.feature.reports.domain

/** A sum beyond what a `Long` holds: shown as "Too large to show" instead of a wrong number (spec edge case, P-03). */
class TooLarge : ArithmeticException("report sum overflow")

/** Checked addition of minor units. */
internal fun addExact(a: Long, b: Long): Long {
    val result = a + b
    if (((a xor result) and (b xor result)) < 0) throw TooLarge()
    return result
}

internal fun subtractExact(a: Long, b: Long): Long {
    val result = a - b
    if (((a xor b) and (a xor result)) < 0) throw TooLarge()
    return result
}
