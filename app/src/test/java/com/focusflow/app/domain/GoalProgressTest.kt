package com.focusflow.app.domain

import com.focusflow.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalProgressTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun percentIsDoneVersusMissedOnly() {
        val p = Progress.of(
            listOf(
                TaskEntity(completed = true), TaskEntity(completed = true), TaskEntity(completed = true),
                TaskEntity(notCompleted = true),
                TaskEntity(), TaskEntity(), // pending: not counted against
            ),
        )
        assertEquals(3, p.done); assertEquals(1, p.missed); assertEquals(2, p.pending)
        assertEquals(75, p.percent)
    }

    @Test fun noPercentBeforeAnythingIsMarked() {
        assertNull(Progress.of(listOf(TaskEntity())).percent)
    }

    @Test fun repeatTasksCollapseIntoOneRoutine() {
        val gym = (1..3).map { TaskEntity(title = "Gym", repeatAgain = 1, seriesId = "s", date = d("2026-11-0$it"), completed = it != 2, notCompleted = it == 2) }
        val oneOff = TaskEntity(title = "Buy shoes", date = d("2026-11-05"))
        val items = GoalItems.build(gym + oneOff)
        assertEquals(2, items.size)
        val routine = items[0] as GoalItem.Routine
        assertEquals("Gym", routine.title)
        assertEquals(3, routine.tasks.size)
        assertEquals(66, routine.progress.percent)
        assertTrue(items[1] is GoalItem.Single)
    }

    @Test fun monthFilterKeepsOnlyThatMonth() {
        val tasks = listOf(
            TaskEntity(title = "Gym", repeatAgain = 1, seriesId = "s", date = d("2026-10-31")),
            TaskEntity(title = "Gym", repeatAgain = 1, seriesId = "s", date = d("2026-11-01")),
            TaskEntity(title = "Idea", date = null),
        )
        val nov = GoalItems.build(tasks, d("2026-11-01"), d("2026-11-30"))
        assertEquals(1, nov.size)
        assertEquals(1, (nov[0] as GoalItem.Routine).tasks.size)
        assertEquals(2, GoalItems.build(tasks).size) // all time includes the undated one
    }
}
