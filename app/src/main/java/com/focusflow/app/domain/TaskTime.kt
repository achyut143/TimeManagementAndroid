package com.focusflow.app.domain

import com.focusflow.app.data.TaskEntity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Helpers for the "H:mm" time strings tasks store (same format as iOS). */
object TaskTime {
    private val pattern = Regex("^\\d{1,2}:\\d{2}$")
    private val display = DateTimeFormatter.ofPattern("h:mm a")

    fun isValid(time: String): Boolean {
        if (!pattern.matches(time.trim())) return false
        val (h, m) = time.trim().split(":").map { it.toInt() }
        return h in 0..23 && m in 0..59
    }

    /** "15:30" -> 930. Anything unparseable -> 0, as on iOS. */
    fun toMinutes(time: String): Int {
        val parts = time.split(":").mapNotNull { it.trim().toIntOrNull() }
        return if (parts.size == 2) parts[0] * 60 + parts[1] else 0
    }

    /** 930 -> "15:30". Wraps around midnight. */
    fun fromMinutes(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return "%d:%02d".format(m / 60, m % 60)
    }

    fun fromLocalTime(time: LocalTime): String = fromMinutes(time.hour * 60 + time.minute)

    fun toLocalTime(time: String): LocalTime {
        val m = toMinutes(time)
        return LocalTime.of(m / 60, m % 60)
    }

    /** Length of start..end in minutes; an end before the start means the task runs past midnight. */
    fun span(start: String, end: String): Int {
        val s = toMinutes(start)
        val e = toMinutes(end)
        return if (e > s) e - s else (24 * 60 - s) + e
    }

    fun displayTime(time: String): String = toLocalTime(time).format(display)

    /** Rounds down to the nearest 5 minutes, like iOS `roundToNearestFiveMinutes`. */
    fun roundedNow(now: LocalTime = LocalTime.now()): LocalTime =
        now.withMinute(now.minute - now.minute % 5).withSecond(0).withNano(0)
}

val TaskEntity.isUntimed: Boolean get() = startTime.isBlank() && endTime.isBlank()
val TaskEntity.isRepeating: Boolean get() = repeatAgain != null
val TaskEntity.isBacklog: Boolean get() = date == null
val TaskEntity.isDone: Boolean get() = completed || notCompleted

/** Allocated minutes: start..end for timed tasks, [TaskEntity.elapsedTime] for untimed ones. */
val TaskEntity.allocatedMinutes: Double
    get() = if (isUntimed) elapsedTime ?: 0.0 else TaskTime.span(startTime, endTime).toDouble()

/** Points earned when completed: full weight, or scaled by time spent / time allocated. */
val TaskEntity.effectiveWeight: Double
    get() {
        val spent = timeSpent ?: return weight
        val allocated = allocatedMinutes
        if (allocated <= 0) return weight
        return weight * (spent / allocated)
    }

fun TaskEntity.startDateTime(): LocalDateTime? {
    val day = date ?: return null
    if (isUntimed) return null
    return day.atTime(TaskTime.toLocalTime(startTime))
}

fun TaskEntity.endDateTime(): LocalDateTime? {
    val start = startDateTime() ?: return null
    return start.plusMinutes(TaskTime.span(startTime, endTime).toLong())
}

/** True while the task's time slot covers [now]. */
fun TaskEntity.isRunning(now: LocalDateTime = LocalDateTime.now()): Boolean {
    val start = startDateTime() ?: return false
    val end = endDateTime() ?: return false
    return !now.isBefore(start) && now.isBefore(end)
}
