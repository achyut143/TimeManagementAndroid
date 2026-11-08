package com.focusflow.app.domain

import com.focusflow.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuickTaskParserTest {
    @Test fun timedWithWeightAndRepeat() {
        val p = QuickTaskParser.parse("15:30 - 16:30 - shovel snow - 2 - r2")!!
        assertEquals("shovel snow", p.title)
        assertEquals("15:30", p.startTime)
        assertEquals("16:30", p.endTime)
        assertEquals(2.0, p.weight, 0.0)
        assertEquals(2, p.repeatDays)
        assertTrue(!p.isUntimed)
    }

    @Test fun untimedDefaults() {
        val p = QuickTaskParser.parse("read book")!!
        assertEquals("read book", p.title)
        assertEquals(1.0, p.weight, 0.0)
        assertNull(p.repeatDays)
        assertTrue(p.isUntimed)
    }

    @Test fun repeatInWeightSlotKeepsDefaultWeight() {
        val p = QuickTaskParser.parse("walk - r1")!!
        assertEquals(1.0, p.weight, 0.0)
    }

    @Test fun commasMakeSeveralTasks() {
        assertEquals(3, QuickTaskParser.parseMultiple("a, b, c").size)
    }
}

class TaskTimeTest {
    @Test fun spanWrapsPastMidnight() {
        assertEquals(60, TaskTime.span("23:30", "0:30"))
        assertEquals(45, TaskTime.span("9:00", "9:45"))
    }

    @Test fun fromMinutesWraps() {
        assertEquals("0:15", TaskTime.fromMinutes(24 * 60 + 15))
        assertEquals("23:50", TaskTime.fromMinutes(-10))
    }

    @Test fun effectiveWeightScalesWithTimeSpent() {
        val t = TaskEntity(title = "x", startTime = "9:00", endTime = "10:00", weight = 2.0, timeSpent = 30.0)
        assertEquals(1.0, t.effectiveWeight, 0.0001)
    }
}

class StatsTest {
    private val d = LocalDate.of(2026, 1, 1)
    private fun habit(day: Int, done: Boolean?, title: String = "run") = TaskEntity(
        title = title,
        date = d.plusDays(day.toLong()),
        repeatAgain = 1,
        completed = done == true,
        notCompleted = done == false,
    )

    @Test fun habitStatsCountsAndStreaks() {
        val tasks = listOf(habit(0, true), habit(1, true), habit(2, false), habit(3, true), habit(4, true), habit(5, true), habit(6, null))
        val s = HabitStatsCalculator.compute(tasks, d, d.plusDays(6)).single()
        assertEquals(5, s.completed)
        assertEquals(1, s.missed)
        assertEquals(3, s.currentStreak) // pending day 6 is skipped
        assertEquals(3, s.maxStreak)
    }

    @Test fun pointsUseEffectiveWeight() {
        val tasks = listOf(
            TaskEntity(title = "a", weight = 2.0, completed = true),
            TaskEntity(title = "b", weight = 2.0),
        )
        val p = PointsCalculator.of(tasks)
        assertEquals(2.0, p.earned, 0.0)
        assertEquals(4.0, p.total, 0.0)
        assertEquals(50, p.percent)
    }

    @Test fun taskMetricsCountPendingDaysAsNotDone() {
        val tasks = listOf(habit(0, true), habit(1, true), habit(2, null))
        val m = TaskMetricsCalculator.calculate("run", tasks, days = 30, reference = d.plusDays(2))
        assertEquals(2, m.completed)
        assertEquals(3, m.total)
        assertEquals(0, m.currentStreak)
    }
}
