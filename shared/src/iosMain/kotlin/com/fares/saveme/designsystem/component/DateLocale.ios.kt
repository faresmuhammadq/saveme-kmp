package com.fares.saveme.designsystem.component

import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerFormatter
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.CalendarLocale
import platform.Foundation.NSLocale

/** The language with Latin digits (`numbers=latn`), so Arabic dates and numbers print 0-9 (P-06.2). */
private fun westernDigitsLocale(language: String): CalendarLocale = NSLocale("$language@numbers=latn")

@OptIn(ExperimentalMaterial3Api::class)
internal actual fun westernDigitsDatePickerState(
    language: String,
    initialSelectedDateMillis: Long,
    yearRange: IntRange,
    selectableDates: SelectableDates,
): DatePickerState = DatePickerState(
    locale = westernDigitsLocale(language),
    initialSelectedDateMillis = initialSelectedDateMillis,
    yearRange = yearRange,
    selectableDates = selectableDates,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun westernDigitsDatePickerFormatter(language: String): DatePickerFormatter {
    val delegate = DatePickerDefaults.dateFormatter()
    return remember(language, delegate) { WesternDatePickerFormatter(delegate, westernDigitsLocale(language)) }
}

@OptIn(ExperimentalMaterial3Api::class)
private class WesternDatePickerFormatter(
    private val delegate: DatePickerFormatter,
    private val locale: CalendarLocale,
) : DatePickerFormatter {
    override fun formatMonthYear(monthMillis: Long?, locale: CalendarLocale): String? = delegate.formatMonthYear(monthMillis, this.locale)

    override fun formatDate(dateMillis: Long?, locale: CalendarLocale, forContentDescription: Boolean): String? =
        delegate.formatDate(dateMillis, this.locale, forContentDescription)
}
