package com.fares.saveme.designsystem.money

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatterTest {

    private val lri = "⁦"
    private val pdi = "⁩"

    @Test
    fun egpInEnglishHasTwoDigitsAndCodePrefix() {
        assertEquals("EGP 450.00", MoneyFormatter.format(45_000, "EGP", "en"))
    }

    @Test
    fun negativeUsesTrueMinusSign() {
        assertEquals("−EGP 450.00", MoneyFormatter.format(-45_000, "EGP", "en"))
    }

    @Test
    fun groupsThousands() {
        assertEquals("EGP 48,215.75", MoneyFormatter.format(4_821_575, "EGP", "en"))
    }

    @Test
    fun fractionDigitsComeFromTheCurrency() {
        assertEquals("KWD 1,234.567", MoneyFormatter.format(1_234_567, "KWD", "en"))
        assertEquals("JPY 500", MoneyFormatter.format(500, "JPY", "en"))
        assertEquals("XYZ 1.05", MoneyFormatter.format(105, "XYZ", "en"))
    }

    @Test
    fun explicitPlusForIncome() {
        assertEquals("+EGP 32,000.00", MoneyFormatter.format(3_200_000, "EGP", "en", showPlus = true))
    }

    @Test
    fun arabicEgpUsesLocalSymbolWithWesternDigitsIsolatedLtr() {
        assertEquals("${lri}450.00 ج.م$pdi", MoneyFormatter.format(45_000, "EGP", "ar"))
        assertEquals("$lri−450.00 ج.م$pdi", MoneyFormatter.format(-45_000, "EGP", "ar"))
    }

    @Test
    fun arabicOtherCurrencyKeepsCode() {
        assertEquals("${lri}KWD 1.500$pdi", MoneyFormatter.format(1_500, "KWD", "ar"))
    }

    @Test
    fun smallAmountsArePadded() {
        assertEquals("EGP 0.05", MoneyFormatter.format(5, "EGP", "en"))
        assertEquals("KWD 0.001", MoneyFormatter.format(1, "KWD", "en"))
    }
}
