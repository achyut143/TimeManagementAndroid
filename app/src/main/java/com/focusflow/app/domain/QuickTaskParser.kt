package com.focusflow.app.domain

/**
 * Port of iOS `QuickTaskParser`.
 *
 * - Timed:   "15:30 - 16:30 - shovel snow - 2 - r2"
 * - Untimed: "shovel snow - 2 - r2"
 * - Weight defaults to 1. "r2" means repeat every 2 days.
 * - Commas separate several tasks.
 */
object QuickTaskParser {
    data class ParsedTask(
        val title: String,
        val startTime: String,
        val endTime: String,
        val weight: Double,
        val repeatDays: Int?,
        val isUntimed: Boolean,
    ) {
        val isValid: Boolean get() = title.isNotEmpty()
    }

    private val timePattern = Regex("^\\d{1,2}:\\d{2}$")

    const val HELP_TEXT = """Quick task format:
• Timed: 15:30 - 16:30 - task name - weight - r2
• Untimed: task name - weight - r2
• Weight defaults to 1 (optional)
• r2 = repeat every 2 days (optional)
• Use commas to add several tasks at once"""

    fun parseMultiple(input: String): List<ParsedTask> =
        input.split(",").mapNotNull { parse(it) }.filter { it.isValid }

    fun parse(input: String): ParsedTask? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split(" - ").map { it.trim() }
        val hasStartTime = timePattern.matches(parts.first())

        return if (hasStartTime && parts.size >= 2) {
            ParsedTask(
                title = parts.getOrElse(2) { "" },
                startTime = parts[0],
                endTime = parts[1],
                weight = parts.getOrNull(3)?.let(::parseWeight) ?: 1.0,
                repeatDays = parts.getOrNull(4)?.let(::parseRepeat),
                isUntimed = false,
            )
        } else {
            ParsedTask(
                title = parts[0],
                startTime = "",
                endTime = "",
                weight = parts.getOrNull(1)?.let(::parseWeight) ?: 1.0,
                repeatDays = parts.getOrNull(2)?.let(::parseRepeat),
                isUntimed = true,
            )
        }
    }

    private fun parseWeight(input: String): Double =
        if (input.lowercase().startsWith("r")) 1.0 else input.toDoubleOrNull() ?: 1.0

    private fun parseRepeat(input: String): Int? {
        val lower = input.lowercase()
        return if (lower.startsWith("r")) lower.drop(1).toIntOrNull() else null
    }
}
