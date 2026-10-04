package com.fares.saveme.designsystem.component

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.DatePickerFormatter
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_done
import kotlin.time.Instant

/** The calendar range every date in the app lives in (backend: 1900-01-01 to 2999-12-31). */
val DefaultMinDate = LocalDate(1900, 1, 1)
val DefaultMaxDate = LocalDate(2999, 12, 31)

/**
 * The day the picker may treat as "today". The Material picker reads the system clock for its today marker and offers
 * no seam, so when a day is pinned here (catalogue and screenshot tests) the marker is suppressed instead and the
 * rendering never depends on the real date. `null` (the default, every shipping screen) keeps the system clock.
 */
val LocalPickerToday: ProvidableCompositionLocal<LocalDate?> = staticCompositionLocalOf { null }

/** A calendar date shown in a field that opens [AppDatePickerDialog]; announced as a button like [AppSelectField]. */
@Composable
fun DateField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    AppSelectField(label, value, onClick, modifier, errorText = errorText, enabled = enabled, trailingIcon = AppIcons.Calendar)
}

/** The picker's locale for [language] ("ar" or "en") with Western digits (0–9) in both (P-06.2); platform specific. */
internal expect fun westernDigitsLocale(language: String): CalendarLocale

/**
 * Formats dates through the given locale whatever locale the picker asks for, so the headline and the month title
 * print Western digits too; the calendar grid already follows the state's locale.
 */
@OptIn(ExperimentalMaterial3Api::class)
private class WesternDatePickerFormatter(
    private val delegate: DatePickerFormatter,
    private val locale: CalendarLocale,
) : DatePickerFormatter {
    override fun formatMonthYear(monthMillis: Long?, locale: CalendarLocale): String? = delegate.formatMonthYear(monthMillis, this.locale)

    override fun formatDate(dateMillis: Long?, locale: CalendarLocale, forContentDescription: Boolean): String? =
        delegate.formatDate(dateMillis, this.locale, forContentDescription)
}

/**
 * The picker's calendar without a window, so the catalogue and screenshots can show it (as the dialog card does).
 * Numbers and dates print Western digits in Arabic and English alike (P-06.2). [onSelect] reports each chosen day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerContent(
    initial: LocalDate,
    modifier: Modifier = Modifier,
    onSelect: (LocalDate) -> Unit = {},
    min: LocalDate = DefaultMinDate,
    max: LocalDate = DefaultMaxDate,
) {
    val language = moneyLanguage()
    val locale = remember(language) { westernDigitsLocale(language) }
    val todayPinned = LocalPickerToday.current != null
    val minMillis = min.toUtcMillis()
    val maxMillis = max.toUtcMillis()
    val state = remember(locale, initial, min, max) {
        DatePickerState(
            locale = locale,
            initialSelectedDateMillis = initial.toUtcMillis(),
            yearRange = min.year..max.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in minMillis..maxMillis
                override fun isSelectableYear(year: Int) = year in min.year..max.year
            },
        )
    }
    LaunchedEffect(state.selectedDateMillis) { state.selectedDateMillis?.let { onSelect(it.toUtcDate()) } }
    DatePicker(
        state = state,
        modifier = modifier,
        dateFormatter = WesternDatePickerFormatter(DatePickerDefaults.dateFormatter(), locale),
        colors = DatePickerDefaults.colors(
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.ink2,
            headlineContentColor = AppTheme.colors.ink,
            weekdayContentColor = AppTheme.colors.ink2,
            subheadContentColor = AppTheme.colors.ink2,
            navigationContentColor = AppTheme.colors.ink,
            yearContentColor = AppTheme.colors.ink,
            currentYearContentColor = AppTheme.colors.primary,
            selectedYearContainerColor = AppTheme.colors.primary,
            selectedYearContentColor = AppTheme.colors.onPrimary,
            dayContentColor = AppTheme.colors.ink,
            selectedDayContainerColor = AppTheme.colors.primary,
            selectedDayContentColor = AppTheme.colors.onPrimary,
            todayContentColor = if (todayPinned) AppTheme.colors.ink else AppTheme.colors.primary,
            todayDateBorderColor = if (todayPinned) Color.Transparent else AppTheme.colors.primary,
        ),
    )
}

/**
 * Choose a calendar date between [min] and [max] (FR-004). Dates are calendar dates with no zone
 * (P-05): the picker's UTC-midnight milliseconds are converted back to the same day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    min: LocalDate = DefaultMinDate,
    max: LocalDate = DefaultMaxDate,
) {
    var selected by remember { mutableStateOf(initial) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { AppTextButton(stringResource(Res.string.action_done), { onPick(selected) }) },
        dismissButton = { AppTextButton(stringResource(Res.string.action_cancel), onDismiss) },
        colors = DatePickerDefaults.colors(containerColor = AppTheme.colors.surface),
    ) {
        AppDatePickerContent(initial, onSelect = { selected = it }, min = min, max = max)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toUtcDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
