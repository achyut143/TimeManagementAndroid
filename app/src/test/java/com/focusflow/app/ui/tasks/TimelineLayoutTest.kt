package com.focusflow.app.ui.tasks

import com.focusflow.app.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineLayoutTest {
    private fun t(id: String, start: String, end: String) = TaskEntity(id = id, title = id, startTime = start, endTime = end)

    @Test fun backToBackTasksEachGetFullWidth() {
        val cols = layoutColumns(listOf(t("a", "8:00", "8:15"), t("b", "8:15", "8:30")))
        assertEquals(0 to 1, cols["a"])
        assertEquals(0 to 1, cols["b"])
    }

    @Test fun overlappingTasksShareWidth() {
        val cols = layoutColumns(listOf(t("a", "8:00", "9:00"), t("b", "8:30", "9:30"), t("c", "9:00", "9:15"), t("d", "10:00", "10:30")))
        assertEquals(0 to 2, cols["a"])
        assertEquals(1 to 2, cols["b"])
        assertEquals(0 to 2, cols["c"]) // reuses a's column once a ends
        assertEquals(0 to 1, cols["d"])
    }
}
