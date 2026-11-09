package com.focusflow.app.domain

import com.focusflow.app.data.TaskEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * How a repeat task repeats. Stored on [TaskEntity] as:
 * - weekly:  [TaskEntity.repeatWeekdays] = "1,3" (ISO days, Mon = 1 … Sun = 7)
 * - monthly: [TaskEntity.repeatMonthDay] = 15
 * - every N days: only [TaskEntity.repeatAgain]
 * [TaskEntity.repeatAgain] is always set on a repeat task, so it still marks the task as a habit.
 */
sealed interface RepeatRule {
    data class EveryDays(val days: Int) : RepeatRule
    data class Weekly(val days: Set<DayOfWeek>) : RepeatRule
    /** Day 29–31 falls on the last day of shorter months. */
    data class Monthly(val dayOfMonth: Int) : RepeatRule

    /** The first matching day after [from]. */
    fun nextAfter(from: LocalDate): LocalDate = when (this) {
        is EveryDays -> from.plusDays(days.coerceAtLeast(1).toLong())
        is Weekly -> {
            val wanted = days.ifEmpty { setOf(from.dayOfWeek) }
            generateSequence(from.plusDays(1)) { it.plusDays(1) }.first { it.dayOfWeek in wanted }
        }
        is Monthly -> {
            val thisMonth = from.withDayOfMonth(dayOfMonth.coerceAtMost(from.lengthOfMonth()))
            if (thisMonth.isAfter(from)) {
                thisMonth
            } else {
                val next = from.plusMonths(1)
                next.withDayOfMonth(dayOfMonth.coerceAtMost(next.lengthOfMonth()))
            }
        }
    }

    /** Short label for cards, e.g. "2d", "Mon, Wed", "15th". */
    fun shortLabel(): String = when (this) {
        is EveryDays -> "${days}d"
        is Weekly -> if (days.size == 7) "daily" else days.sorted().joinToString(", ") { it.shortName() }
        is Monthly -> ordinal(dayOfMonth)
    }

    /** Full label, e.g. "repeats every 2 days", "repeats every Mon, Wed", "repeats monthly on the 15th". */
    fun longLabel(): String = when (this) {
        is EveryDays -> "repeats every $days day${if (days == 1) "" else "s"}"
        is Weekly -> "repeats every ${shortLabel()}"
        is Monthly -> "repeats monthly on the ${ordinal(dayOfMonth)}"
    }

    /** Rough gap in days, for code that needs a number (habit stats, the stored [TaskEntity.repeatAgain]). */
    fun approxDays(): Int = when (this) {
        is EveryDays -> days
        is Weekly -> if (days.isEmpty()) 7 else maxOf(1, 7 / days.size)
        is Monthly -> 30
    }

    companion object {
        fun ordinal(n: Int): String {
            val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
            return "$n$suffix"
        }

        fun parseWeekdays(stored: String?): Set<DayOfWeek> =
            stored.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull()?.takeIf { d -> d in 1..7 } }
                .map(DayOfWeek::of).toSet()

        fun formatWeekdays(days: Set<DayOfWeek>): String? =
            days.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",") { it.value.toString() }
    }
}

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** The task's repeat rule, or null if it doesn't repeat. */
val TaskEntity.repeatRule: RepeatRule?
    get() {
        val days = repeatAgain ?: return null
        val weekdays = RepeatRule.parseWeekdays(repeatWeekdays)
        return when {
            weekdays.isNotEmpty() -> RepeatRule.Weekly(weekdays)
            repeatMonthDay != null -> RepeatRule.Monthly(repeatMonthDay.coerceIn(1, 31))
            else -> RepeatRule.EveryDays(days)
        }
    }

/** Copies [rule] onto the task's stored fields (null clears repeating). */
fun TaskEntity.withRepeatRule(rule: RepeatRule?): TaskEntity = when (rule) {
    null -> copy(repeatAgain = null, repeatWeekdays = null, repeatMonthDay = null, repeatNumber = null, repeatUntil = null)
    else -> copy(
        repeatAgain = rule.approxDays(),
        repeatWeekdays = (rule as? RepeatRule.Weekly)?.let { RepeatRule.formatWeekdays(it.days) },
        repeatMonthDay = (rule as? RepeatRule.Monthly)?.dayOfMonth,
        repeatNumber = repeatNumber ?: 1,
    )
}

/** Works out which repeat tasks belong on a day, so they show up before the earlier one is marked. */
object RepeatPlanner {
    data class Planned(val template: TaskEntity, val repeatNumber: Int)

    /**
     * [series] is every task of one repeat series dated on or before [day].
     * Returns what to create on [day], or null if nothing should be (already there,
     * repeating was turned off, or the rule doesn't land on [day]).
     */
    fun plan(day: LocalDate, series: List<TaskEntity>): Planned? {
        val dated = series.filter { it.date != null && !it.date.isAfter(day) }
        if (dated.isEmpty() || dated.any { it.date == day }) return null
        val latest = dated.maxBy { it.date!! }
        val rule = latest.repeatRule ?: return null // repeat turned off: the series stops here
        if (latest.repeatUntil != null && day.isAfter(latest.repeatUntil)) return null // past the end date
        // Step from the last real occurrence; roll-over copies (red icon) sit off the schedule.
        val source = dated.filter { !it.reassign && it.isRepeating }.maxByOrNull { it.date!! } ?: latest
        var d = source.date!!
        var n = source.repeatNumber ?: 1
        var guard = 0
        while (d.isBefore(day) && guard++ < 10_000) {
            d = rule.nextAfter(d)
            n++
        }
        return if (d == day) Planned(latest, n) else null
    }
}
