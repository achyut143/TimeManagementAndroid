package com.focusflow.app.domain

import com.focusflow.app.data.TaskEntity
import java.time.LocalDate

data class Points(val earned: Double, val total: Double) {
    /** Whole-number percentage, 0 when there is nothing to earn. Can go above 100. */
    val percent: Int get() = if (total > 0) ((earned / total) * 100).toInt() else 0
}

object PointsCalculator {
    /** Same as iOS: total = sum of weights, earned = effective weight of completed tasks. */
    fun of(tasks: List<TaskEntity>): Points =
        Points(
            earned = tasks.filter { it.completed }.sumOf { it.effectiveWeight },
            total = tasks.sumOf { it.weight },
        )

    fun byDay(tasks: List<TaskEntity>): Map<LocalDate, Points> =
        tasks.filter { it.date != null }.groupBy { it.date!! }.mapValues { of(it.value) }
}

/** Completion rate and streak shown on a repeat task's card (port of iOS `TaskMetrics`). */
data class TaskMetrics(
    val completionRate: Double,
    val currentStreak: Int,
    val completed: Int,
    val total: Int,
)

object TaskMetricsCalculator {
    /**
     * Looks at the last [days] days up to [reference] for repeat tasks with this [title].
     * Each day that has one counts once; it is "completed" if any of that day's copies is.
     */
    fun calculate(title: String, repeating: List<TaskEntity>, days: Int, reference: LocalDate): TaskMetrics {
        val start = reference.minusDays(days.toLong())
        val byDay = repeating
            .filter { it.title == title && it.date != null && it.date in start..reference }
            .groupBy { it.date!! }

        val daily = byDay.keys.sorted().map { day -> byDay.getValue(day).any { it.completed } }
        val completed = daily.count { it }
        val total = daily.size
        return TaskMetrics(
            completionRate = if (total > 0) completed * 100.0 / total else 0.0,
            currentStreak = daily.asReversed().takeWhile { it }.size,
            completed = completed,
            total = total,
        )
    }
}

/** One row in the Habits screen (port of iOS `HabitStats` plus points). */
data class HabitStats(
    val name: String,
    val completed: Int,
    val missed: Int,
    val percentage: Double,
    val currentStreak: Int,
    val maxStreak: Int,
    val earned: Double,
    val allocated: Double,
    val repeatInterval: Int,
    /** e.g. "2d", "Mon, Wed", "15th". */
    val repeatLabel: String = "${repeatInterval}d",
    val tags: List<String>,
    /** Completed (true) or missed (false) for each day in range that has a result. */
    val days: Map<LocalDate, Boolean>,
) {
    val total: Int get() = completed + missed
}

object HabitStatsCalculator {
    /**
     * A habit is every repeat task with the same title. For each day in [from]..[to]:
     * completed if any copy is completed, else missed if any copy is marked not completed,
     * else (still pending) the day is skipped.
     */
    fun compute(tasks: List<TaskEntity>, from: LocalDate, to: LocalDate): List<HabitStats> {
        val inRange = tasks.filter { it.isRepeating && it.date != null && it.date in from..to }
        return inRange.groupBy { it.title }.map { (name, habitTasks) ->
            val days = sortedMapOf<LocalDate, Boolean>()
            habitTasks.groupBy { it.date!! }.forEach { (day, dayTasks) ->
                when {
                    dayTasks.any { it.completed } -> days[day] = true
                    dayTasks.any { it.notCompleted } -> days[day] = false
                }
            }
            val results = days.values.toList()
            val completed = results.count { it }
            val missed = results.size - completed
            HabitStats(
                name = name,
                completed = completed,
                missed = missed,
                percentage = if (results.isNotEmpty()) completed * 100.0 / results.size else 0.0,
                currentStreak = results.asReversed().takeWhile { it }.size,
                maxStreak = longestRun(results),
                earned = habitTasks.filter { it.completed }.sumOf { it.effectiveWeight },
                allocated = habitTasks.sumOf { it.weight },
                repeatInterval = habitTasks.first().repeatAgain ?: 1,
                repeatLabel = habitTasks.maxBy { it.date!! }.repeatRule?.shortLabel() ?: "${habitTasks.first().repeatAgain ?: 1}d",
                tags = habitTasks.flatMap { it.tags.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                days = days,
            )
        }
    }

    private fun longestRun(results: List<Boolean>): Int {
        var best = 0
        var run = 0
        for (done in results) {
            run = if (done) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }
}
