package com.focusflow.app.domain

import com.focusflow.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class RepeatRuleTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun everyDays() {
        assertEquals(d("2026-11-10"), RepeatRule.EveryDays(2).nextAfter(d("2026-11-08")))
    }

    @Test fun weeklyMondayGoesToNextMonday() {
        // 2026-11-09 is a Monday.
        assertEquals(d("2026-11-16"), RepeatRule.Weekly(setOf(MONDAY)).nextAfter(d("2026-11-09")))
    }

    @Test fun weeklyTwoDaysAlternates() {
        val rule = RepeatRule.Weekly(setOf(TUESDAY, WEDNESDAY))
        assertEquals(d("2026-11-11"), rule.nextAfter(d("2026-11-10"))) // Tue -> Wed
        assertEquals(d("2026-11-17"), rule.nextAfter(d("2026-11-11"))) // Wed -> next Tue
    }

    @Test fun monthlyOn15th() {
        val rule = RepeatRule.Monthly(15)
        assertEquals(d("2026-12-15"), rule.nextAfter(d("2026-11-15")))
        assertEquals(d("2026-11-15"), rule.nextAfter(d("2026-11-08"))) // still ahead this month
    }

    @Test fun monthly31stUsesLastDayOfShortMonths() {
        val rule = RepeatRule.Monthly(31)
        assertEquals(d("2027-02-28"), rule.nextAfter(d("2027-01-31")))
        assertEquals(d("2027-03-31"), rule.nextAfter(d("2027-02-28")))
    }

    @Test fun storedFieldsRoundTrip() {
        val weekly = TaskEntity().withRepeatRule(RepeatRule.Weekly(setOf(WEDNESDAY, MONDAY)))
        assertEquals("1,3", weekly.repeatWeekdays)
        assertEquals(1, weekly.repeatNumber)
        assertEquals(RepeatRule.Weekly(setOf(MONDAY, WEDNESDAY)), weekly.repeatRule)

        val monthly = TaskEntity().withRepeatRule(RepeatRule.Monthly(15))
        assertEquals(RepeatRule.Monthly(15), monthly.repeatRule)
        assertEquals("15th", monthly.repeatRule!!.shortLabel())

        val none = monthly.withRepeatRule(null)
        assertNull(none.repeatRule)
        assertNull(none.repeatNumber)
    }

    @Test fun oldEveryNDaysTasksStillWork() {
        assertEquals(RepeatRule.EveryDays(3), TaskEntity(repeatAgain = 3).repeatRule)
    }
}
