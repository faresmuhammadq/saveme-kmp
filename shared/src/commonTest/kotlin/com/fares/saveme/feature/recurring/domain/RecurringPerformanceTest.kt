package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleDraft
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/** SC-008: fifty rules and two thousand occurrences derive into the lists in under a second; a 120-round preview is instant. */
class RecurringPerformanceTest {

    @Test
    fun fiftyRulesAndTwoThousandOccurrencesDeriveUnderASecond() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        val start = LocalDate(2026, 6, 1)
        repeat(50) { r ->
            env.seedRule(ruleJson(id = "r$r", frequency = "monthly", startDate = "2026-06-01"))
            repeat(40) { o ->
                env.seedOccurrence(occurrenceJson(id = "o$r-$o", ruleId = "r$r", ordinal = o + 1, scheduledDate = start.plus(o * 7, DateTimeUnit.DAY).toString(), changeSeq = 1))
            }
        }
        var rules = 0
        var total = 0
        val took = measureTime {
            rules = env.service.observeRules(null).first().size
            val lists = env.service.observeOccurrences(null).first()
            total = lists.due.size + lists.upcoming.size + lists.history.size
        }
        assertEquals(50, rules)
        assertEquals(2000, total)
        assertTrue(took < 1.seconds, "took $took")
    }

    @Test
    fun aHundredAndTwentyRoundPreviewTakesUnderFiveMilliseconds() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        val draft = ruleDraft(preset = Preset.Gameya, count = 120, payoutRound = 60, payoutCategory = "ci", amount = 100_000)
        repeat(30) { env.service.preview(draft) }
        val best = (1..20).minOf { measureTime { env.service.preview(draft) } }
        assertTrue(best < 5.milliseconds, "best of 20: $best")
    }
}
