package com.focusflow.app.domain

import com.focusflow.app.data.TaskEntity
import java.time.LocalDate

/** Done vs missed for a set of tasks. Tasks not marked yet (pending) don't count against the percentage. */
data class Progress(val done: Int, val missed: Int, val pending: Int) {
    val total: Int get() = done + missed + pending
    /** done ÷ (done + missed), or null when nothing has been marked yet. */
    val percent: Int? get() = if (done + missed > 0) done * 100 / (done + missed) else null

    companion object {
        fun of(tasks: List<TaskEntity>) = Progress(
            done = tasks.count { it.completed },
            missed = tasks.count { it.notCompleted && !it.completed },
            pending = tasks.count { !it.isDone },
        )
    }
}

/** What a goal's page lists: one-off tasks, and repeat tasks collapsed into one routine each. */
sealed interface GoalItem {
    val sortDate: LocalDate?

    data class Single(val task: TaskEntity) : GoalItem {
        override val sortDate get() = task.date
    }

    data class Routine(val title: String, val repeatLabel: String?, val tasks: List<TaskEntity>) : GoalItem {
        val progress: Progress get() = Progress.of(tasks)
        override val sortDate get() = tasks.firstOrNull()?.date
    }
}

object GoalItems {
    /**
     * Tasks of a goal dated within [from]..[to], or every task (undated Backlog ones too) when the range is null.
     * Repeat tasks with the same title (like the Habits screen) become one [GoalItem.Routine], days in order.
     * Routines come first (A–Z), then one-off tasks by date, undated last.
     */
    fun build(tasks: List<TaskEntity>, from: LocalDate? = null, to: LocalDate? = null): List<GoalItem> {
        val inRange = if (from == null || to == null) tasks else tasks.filter { it.date != null && it.date in from..to }
        val (repeating, single) = inRange.partition { it.isRepeating || it.seriesId != null }
        val routines = repeating.groupBy { it.title }.map { (title, list) ->
            val sorted = list.sortedWith(compareBy(nullsLast()) { it.date })
            GoalItem.Routine(title, sorted.lastOrNull { it.isRepeating }?.repeatRule?.shortLabel(), sorted)
        }.sortedBy { it.title.lowercase() }
        val singles = single.map { GoalItem.Single(it) }
            .sortedWith(compareBy<GoalItem.Single, LocalDate?>(nullsLast()) { it.sortDate }.thenBy { it.task.startTime })
        return routines + singles
    }
}
