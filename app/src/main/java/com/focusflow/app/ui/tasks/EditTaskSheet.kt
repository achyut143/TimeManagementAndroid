package com.focusflow.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focusflow.app.data.GoalEntity
import com.focusflow.app.data.TaskEntity
import com.focusflow.app.domain.RepeatRule
import com.focusflow.app.domain.TaskTime
import com.focusflow.app.domain.repeatRule
import com.focusflow.app.domain.shortName
import com.focusflow.app.domain.withRepeatRule
import com.focusflow.app.domain.isUntimed
import com.focusflow.app.ui.common.DatePickerDialogFor
import com.focusflow.app.ui.common.TimePickerDialog
import com.focusflow.app.ui.common.longLabel
import com.focusflow.app.ui.common.pts
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Create or edit a task (Android version of the iOS task form / EditTaskView). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTaskSheet(
    initial: TaskEntity,
    isNew: Boolean,
    goals: List<GoalEntity>,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var tags by remember { mutableStateOf(initial.tags) }
    var info by remember { mutableStateOf(initial.info.orEmpty()) }
    var notes by remember { mutableStateOf(initial.notes.orEmpty()) }
    // An existing backlog task keeps no date until one is picked.
    var date by remember { mutableStateOf(if (isNew) initial.date ?: LocalDate.now() else initial.date) }
    var untimed by remember { mutableStateOf(initial.isUntimed) }
    var start by remember {
        mutableStateOf(if (initial.isUntimed) TaskTime.roundedNow() else TaskTime.toLocalTime(initial.startTime))
    }
    var duration by remember {
        mutableStateOf(if (initial.isUntimed) "15" else TaskTime.span(initial.startTime, initial.endTime).toString())
    }
    var weight by remember { mutableStateOf(initial.weight.pts()) }
    var priority by remember { mutableStateOf(initial.priority) }
    val initialRule = initial.repeatRule
    var repeatMode by remember {
        mutableStateOf(
            when (initialRule) {
                null -> RepeatMode.None
                is RepeatRule.EveryDays -> RepeatMode.Days
                is RepeatRule.Weekly -> RepeatMode.Weekly
                is RepeatRule.Monthly -> RepeatMode.Monthly
            },
        )
    }
    var repeatDays by remember { mutableStateOf((initialRule as? RepeatRule.EveryDays)?.days?.toString() ?: "1") }
    var weekdays by remember {
        mutableStateOf((initialRule as? RepeatRule.Weekly)?.days ?: setOf((initial.date ?: LocalDate.now()).dayOfWeek))
    }
    var monthDay by remember {
        mutableStateOf(((initialRule as? RepeatRule.Monthly)?.dayOfMonth ?: (initial.date ?: LocalDate.now()).dayOfMonth).toString())
    }
    var copySubtasks by remember { mutableStateOf(initial.copySubtasks) }
    var goalId by remember { mutableStateOf(initial.goalId) }
    var timeSpent by remember { mutableStateOf(initial.timeSpent?.toInt()?.toString().orEmpty()) }
    var allocated by remember { mutableStateOf(initial.elapsedTime?.toInt()?.toString().orEmpty()) }

    var repeatUntil by remember { mutableStateOf(initial.repeatUntil) }
    var pickUntil by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeFormat = remember { DateTimeFormatter.ofPattern("h:mm a") }

    val durationMinutes = duration.toIntOrNull()?.coerceIn(1, 24 * 60)
    val rule: RepeatRule? = when (repeatMode) {
        RepeatMode.None -> null
        RepeatMode.Days -> repeatDays.toIntOrNull()?.takeIf { it > 0 }?.let { RepeatRule.EveryDays(it) }
        RepeatMode.Weekly -> weekdays.takeIf { it.isNotEmpty() }?.let { RepeatRule.Weekly(it) }
        RepeatMode.Monthly -> monthDay.toIntOrNull()?.takeIf { it in 1..31 }?.let { RepeatRule.Monthly(it) }
    }
    val untilValid = rule == null || repeatUntil == null || date == null || !repeatUntil!!.isBefore(date)
    val repeatValid = (repeatMode == RepeatMode.None || rule != null) && untilValid
    val canSave = title.isNotBlank() && (untimed || durationMinutes != null) && repeatValid

    fun build(): TaskEntity = initial.copy(
        title = title.trim(),
        tags = tags.trim(),
        info = info.trim().ifEmpty { null },
        notes = notes.trim().ifEmpty { null },
        date = date,
        startTime = if (untimed || date == null) "" else TaskTime.fromLocalTime(start),
        endTime = if (untimed || date == null) "" else TaskTime.fromLocalTime(start.plusMinutes((durationMinutes ?: 15).toLong())),
        weight = weight.toDoubleOrNull() ?: 1.0,
        priority = priority,
        copySubtasks = copySubtasks,
        goalId = goalId,
        timeSpent = timeSpent.toDoubleOrNull(),
        elapsedTime = if (untimed) allocated.toDoubleOrNull() else initial.elapsedTime,
    ).withRepeatRule(rule).let { if (rule != null) it.copy(repeatUntil = repeatUntil) else it }

    // Scrolling the form to its top must not drag the sheet closed: swallow the
    // leftover scroll / fling here. Dragging the handle bar still closes it.
    val keepSheetOpen = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().nestedScroll(keepSheetOpen).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isNew) "New task" else "Edit task",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(build()) }, enabled = canSave) { Text("Save") }
            }

            // Long titles wrap onto up to 4 lines, then scroll inside the box. Enter doesn't add new lines.
            OutlinedTextField(
                title, { title = it.replace('\n', ' ') }, Modifier.fillMaxWidth(),
                label = { Text("Title") }, minLines = 1, maxLines = 4,
            )
            OutlinedTextField(
                tags, { tags = it }, Modifier.fillMaxWidth(),
                label = { Text("Tags (comma separated)") }, singleLine = true,
            )

            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(date?.let { "Date: ${it.longLabel()}" } ?: "No date (in Backlog) · tap to schedule")
            }

            if (date != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Untimed", modifier = Modifier.weight(1f))
                    Switch(checked = untimed, onCheckedChange = { untimed = it })
                }
            }
            if (date == null) {
                // Backlog task: no time until it's scheduled.
            } else if (untimed) {
                NumberField("Allocated minutes (optional)", allocated) { allocated = it }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f)) {
                        Text("Start ${start.format(timeFormat)}")
                    }
                    NumberField("Minutes", duration, Modifier.weight(1f)) { duration = it }
                }
                durationMinutes?.let {
                    Text(
                        "Ends ${start.plusMinutes(it.toLong()).format(timeFormat)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { m ->
                        TextButton(onClick = { duration = m.toString() }) { Text("${m}m") }
                    }
                }
            }

            NumberField("Weight (points)", weight, decimal = true) { weight = it }

            Text("Repeat", style = MaterialTheme.typography.labelLarge)
            val modes = RepeatMode.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = repeatMode == m,
                        onClick = { repeatMode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(m.label, maxLines = 1) }
                }
            }
            when (repeatMode) {
                RepeatMode.None -> {}
                RepeatMode.Days -> NumberField("Repeat every N days", repeatDays) { repeatDays = it }
                RepeatMode.Weekly -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in weekdays,
                                onClick = { weekdays = if (day in weekdays) weekdays - day else weekdays + day },
                                label = { Text(day.shortName()) },
                            )
                        }
                    }
                    if (weekdays.isEmpty()) {
                        Text("Pick at least one day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                RepeatMode.Monthly -> {
                    NumberField("Day of month (1–31)", monthDay) { if (it.length <= 2) monthDay = it }
                    val d = monthDay.toIntOrNull()
                    Text(
                        when {
                            d == null || d !in 1..31 -> "Enter a day from 1 to 31"
                            d > 28 -> "Every month on the ${RepeatRule.ordinal(d)} (last day in shorter months)"
                            else -> "Every month on the ${RepeatRule.ordinal(d)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d == null || d !in 1..31) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (repeatMode != RepeatMode.None) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ends", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        SegmentedButton(
                            selected = repeatUntil == null,
                            onClick = { repeatUntil = null },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("Never") }
                        SegmentedButton(
                            selected = repeatUntil != null,
                            onClick = {
                                if (repeatUntil == null) repeatUntil = (date ?: LocalDate.now()).plusMonths(1)
                                pickUntil = true
                            },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("On date") }
                    }
                }
                repeatUntil?.let { until ->
                    OutlinedButton(onClick = { pickUntil = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Last repeat on or before ${until.longLabel()}")
                    }
                    if (!untilValid) {
                        Text(
                            "End date can't be before the task's date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (rule != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Copy subtasks to next repeat", modifier = Modifier.weight(1f))
                    Switch(checked = copySubtasks, onCheckedChange = { copySubtasks = it })
                }
            }

            Text("Priority", style = MaterialTheme.typography.labelLarge)
            val priorities = listOf("P1", "P2", "P3", "P4")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                priorities.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = priority == p,
                        onClick = { priority = p },
                        shape = SegmentedButtonDefaults.itemShape(i, priorities.size),
                    ) { Text(p) }
                }
            }

            GoalPicker(goals, goalId) { goalId = it }

            NumberField("Time spent (minutes)", timeSpent) { timeSpent = it }
            OutlinedTextField(info, { info = it }, Modifier.fillMaxWidth(), label = { Text("Info") }, minLines = 2)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 3)
            Spacer(Modifier.size(24.dp))
        }
    }

    if (pickUntil) {
        DatePickerDialogFor(repeatUntil ?: date ?: LocalDate.now(), onDismiss = { pickUntil = false }) { repeatUntil = it }
    }
    if (pickDate) DatePickerDialogFor(date ?: LocalDate.now(), onDismiss = { pickDate = false }) { date = it }
    if (pickStart) TimePickerDialog(start, onDismiss = { pickStart = false }) { start = it }
}

private enum class RepeatMode(val label: String) { None("None"), Days("Days"), Weekly("Weekly"), Monthly("Monthly") }

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    decimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() || (decimal && it == '.') }) onChange(v) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalPicker(goals: List<GoalEntity>, selectedId: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = goals.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Goal") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); expanded = false })
            goals.forEach { goal ->
                DropdownMenuItem(text = { Text(goal.name) }, onClick = { onSelect(goal.id); expanded = false })
            }
        }
    }
}
