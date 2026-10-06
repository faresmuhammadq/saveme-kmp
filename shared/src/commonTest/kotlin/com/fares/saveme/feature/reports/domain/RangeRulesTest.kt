package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.domain.model.RangeProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** FR-009: the server's Report Range rule. */
class RangeRulesTest {

    @Test
    fun aSingleDayAndAWholeYearAreAccepted() {
        assertNull(RangeRules.validate(date("2026-10-01"), date("2026-10-01")))
        assertNull(RangeRules.validate(date("2026-01-01"), date("2027-01-01")))
        assertEquals(366, RangeRules.daysInclusive(date("2026-01-01"), date("2027-01-01")))
    }

    @Test
    fun oneDayMoreThanThreeHundredSixtySixIsRefused() {
        assertEquals(RangeProblem.TooLong, RangeRules.validate(date("2026-01-01"), date("2027-01-02")))
    }

    @Test
    fun theStartMustNotBeAfterTheEnd() {
        assertEquals(RangeProblem.Order, RangeRules.validate(date("2026-10-02"), date("2026-10-01")))
    }

    @Test
    fun datesOutsideNineteenHundredToTwentyNineNinetyNineAreRefused() {
        assertNull(RangeRules.validate(date("1900-01-01"), date("1900-01-31")))
        assertNull(RangeRules.validate(date("2999-12-01"), date("2999-12-31")))
        assertEquals(RangeProblem.OutOfBounds, RangeRules.validate(date("1899-12-31"), date("1900-01-05")))
        assertEquals(RangeProblem.OutOfBounds, RangeRules.validate(date("2999-12-30"), date("3000-01-01")))
    }
}
