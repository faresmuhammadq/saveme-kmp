package com.fares.saveme.designsystem.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.month_short
import kotlin.time.Instant

/**
 * Dates for people: Western digits in both languages (P-06.2), month names from resources
 * (P-06.1), and a timezone applied only here, when presenting (P-05).
 */
object DateTimeFormat {

    fun dateTime(instant: Instant, timeZoneId: String, months: List<String>, arabic: Boolean): String {
        val local = instant.toLocalDateTime(zoneOrUtc(timeZoneId))
        val time = "${two(local.hour)}:${two(local.minute)}"
        return date(local.date, months) + (if (arabic) "، " else ", ") + time
    }

    fun date(date: LocalDate, months: List<String>): String =
        "${date.day} ${months[date.month.ordinal]} ${date.year}"

    private fun zoneOrUtc(id: String): TimeZone = runCatching { TimeZone.of(id) }.getOrElse { TimeZone.UTC }

    private fun two(value: Int) = value.toString().padStart(2, '0')
}

@Composable
fun formatDateTime(instant: Instant, timeZoneId: String): String =
    DateTimeFormat.dateTime(
        instant,
        timeZoneId,
        stringArrayResource(Res.array.month_short),
        arabic = LocalLayoutDirection.current == LayoutDirection.Rtl,
    )

@Composable
fun formatDate(date: LocalDate): String = DateTimeFormat.date(date, stringArrayResource(Res.array.month_short))

@Composable
fun formatDate(instant: Instant, timeZoneId: String): String =
    formatDate(instant.toLocalDateTime(runCatching { TimeZone.of(timeZoneId) }.getOrElse { TimeZone.UTC }).date)
