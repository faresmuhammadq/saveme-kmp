package com.fares.saveme.feature.recurring.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.feature.recurring.domain.model.Frequency
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.month_short
import saveme.shared.generated.resources.recurring_cadence_daily
import saveme.shared.generated.resources.recurring_cadence_monthly
import saveme.shared.generated.resources.recurring_cadence_times
import saveme.shared.generated.resources.recurring_cadence_unknown
import saveme.shared.generated.resources.recurring_cadence_until
import saveme.shared.generated.resources.recurring_cadence_weekly
import saveme.shared.generated.resources.recurring_cadence_yearly
import saveme.shared.generated.resources.recurring_weekdays

/** The plural resource that words a frequency; `null` for a frequency this app does not know (R-15). */
fun Frequency.cadenceRes(): PluralStringResource? = when (this) {
    Frequency.Daily -> Res.plurals.recurring_cadence_daily
    Frequency.Weekly -> Res.plurals.recurring_cadence_weekly
    Frequency.Monthly -> Res.plurals.recurring_cadence_monthly
    Frequency.Yearly -> Res.plurals.recurring_cadence_yearly
    Frequency.Unknown -> null
}

/**
 * "Every 2 weeks on Monday", "Every month on day 31", "Every year on 29 Feb". The weekday is the start's weekday, the
 * month day is [anchorDay] (else the start's day), Western digits in both languages (P-06.2).
 */
@Composable
fun cadenceText(frequency: Frequency, interval: Int, anchorDay: Int?, startDate: LocalDate): String {
    val res = frequency.cadenceRes() ?: return stringResource(Res.string.recurring_cadence_unknown)
    val every = interval.coerceAtLeast(1)
    val day = anchorDay ?: startDate.day
    return when (frequency) {
        Frequency.Daily -> pluralStringResource(res, every, every)
        Frequency.Weekly -> pluralStringResource(res, every, every, stringArrayResource(Res.array.recurring_weekdays)[startDate.dayOfWeek.ordinal])
        Frequency.Monthly -> pluralStringResource(res, every, every, day)
        Frequency.Yearly -> {
            val months = stringArrayResource(Res.array.month_short)
            pluralStringResource(res, every, every, "$day ${months[startDate.month.ordinal]}")
        }
        Frequency.Unknown -> stringResource(Res.string.recurring_cadence_unknown)
    }
}

/** "Until 1 Dec 2027", "After 12 times", or `null` when the rule never ends. */
@Composable
fun cadenceEndText(endDate: LocalDate?, occurrenceCount: Int?): String? = when {
    endDate != null -> stringResource(Res.string.recurring_cadence_until, formatDate(endDate))
    occurrenceCount != null -> pluralStringResource(Res.plurals.recurring_cadence_times, occurrenceCount, occurrenceCount)
    else -> null
}
