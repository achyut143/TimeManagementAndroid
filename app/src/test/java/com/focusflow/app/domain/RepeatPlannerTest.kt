package com.focusflow.app.domain

import com.focusflow.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate

class RepeatPlannerTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun task(date: String, rule: RepeatRule?, number: Int = 1, reassign: Boolean = false) =
        TaskEntity(title = "Gym", date = d(date), seriesId = "s1", reassign = reassign)
            .withRepeatRule(rule).copy(repeatNumber = if (rule != null) number else null)

    @Test fun weeklyShowsUpWeeksAheadWithRightNumber() {
        // Monday 2026-11-09 is #1; three Mondays later is #4.
        val plan = RepeatPlanner.plan(d("2026-11-30"), listOf(task("2026-11-09", RepeatRule.Weekly(setOf(MONDAY)))))!!
        assertEquals(4, plan.repeatNumber)
    }

    @Test fun notOnOffDays() {
        assertNull(RepeatPlanner.plan(d("2026-11-10"), listOf(task("2026-11-09", RepeatRule.Weekly(setOf(MONDAY))))))
    }

    @Test fun monthly15th() {
        val plan = RepeatPlanner.plan(d("2027-01-15"), listOf(task("2026-11-15", RepeatRule.Monthly(15), number = 2)))!!
        assertEquals(4, plan.repeatNumber)
    }

    @Test fun nothingIfTheDayAlreadyHasIt() {
        val rule = RepeatRule.EveryDays(1)
        assertNull(RepeatPlanner.plan(d("2026-11-09"), listOf(task("2026-11-08", rule), task("2026-11-09", rule, 2))))
    }

    @Test fun stopsOnceRepeatIsTurnedOff() {
        val rule = RepeatRule.EveryDays(1)
        val series = listOf(task("2026-11-08", rule), task("2026-11-09", null))
        assertNull(RepeatPlanner.plan(d("2026-11-10"), series))
    }

    @Test fun rollOverCopyDoesNotShiftTheSchedule() {
        val rule = RepeatRule.EveryDays(3)
        // Missed on the 8th, carried over to the 9th; the next real one is still the 11th.
        val series = listOf(task("2026-11-08", rule), task("2026-11-09", rule, reassign = true))
        assertNull(RepeatPlanner.plan(d("2026-11-12"), series))
        assertEquals(2, RepeatPlanner.plan(d("2026-11-11"), series)!!.repeatNumber)
    }

    @Test fun stopsAfterEndDate() {
        val start = task("2026-11-09", RepeatRule.Weekly(setOf(MONDAY))).copy(repeatUntil = d("2026-11-23"))
        assertEquals(3, RepeatPlanner.plan(d("2026-11-23"), listOf(start))!!.repeatNumber) // last day still counts
        assertNull(RepeatPlanner.plan(d("2026-11-30"), listOf(start)))
    }

    @Test fun turningRepeatOffClearsEndDate() {
        val t = task("2026-11-09", RepeatRule.Monthly(9)).copy(repeatUntil = d("2027-01-01"))
        assertNull(t.withRepeatRule(null).repeatUntil)
    }
}
