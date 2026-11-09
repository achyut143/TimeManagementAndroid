package com.focusflow.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusflow.app.AppContainer
import com.focusflow.app.data.TaskEntity
import com.focusflow.app.domain.HabitStats
import com.focusflow.app.domain.HabitStatsCalculator
import com.focusflow.app.ui.common.DateRangePickerDialog
import com.focusflow.app.ui.common.NoInsets
import com.focusflow.app.ui.common.containerFactory
import com.focusflow.app.ui.common.pts
import com.focusflow.app.ui.common.shortLabel
import com.focusflow.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class HabitsViewModel(private val c: AppContainer) : ViewModel() {
    // Opens on the current month, like iOS.
    private val _range = MutableStateFlow(YearMonth.now().let { it.atDay(1) to it.atEndOfMonth() })
    val range: StateFlow<Pair<LocalDate, LocalDate>> = _range.asStateFlow()

    /** Every repeat task, so a habit's calendar can show any month. */
    val repeating: StateFlow<List<TaskEntity>> = c.db.taskDao().observeRepeating()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val habits: StateFlow<List<HabitStats>> =
        combine(repeating, _range) { tasks, (from, to) ->
            HabitStatsCalculator.compute(tasks, from, to)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRange(from: LocalDate, to: LocalDate) { _range.value = from to to }

    fun stopRoutine(name: String) = viewModelScope.launch { c.repository.stopRoutine(name) }
    fun deleteRoutine(name: String) = viewModelScope.launch { c.repository.deleteRoutine(name) }

    /** Moves the range by its own length (previous / next month when a full month is shown). */
    fun shift(forward: Boolean) {
        val (from, to) = _range.value
        val isFullMonth = from.dayOfMonth == 1 && to == YearMonth.from(from).atEndOfMonth()
        _range.value = if (isFullMonth) {
            val m = YearMonth.from(from).plusMonths(if (forward) 1 else -1)
            m.atDay(1) to m.atEndOfMonth()
        } else {
            val days = to.toEpochDay() - from.toEpochDay() + 1
            val step = if (forward) days else -days
            from.plusDays(step) to to.plusDays(step)
        }
    }

    companion object {
        val Factory = containerFactory { HabitsViewModel(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitsScreen(vm: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory)) {
    val range by vm.range.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val repeating by vm.repeating.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("desc") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var pickRange by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<HabitStats?>(null) }
    var toDelete by remember { mutableStateOf<String?>(null) }

    val allTags = habits.flatMap { it.tags }.distinct().sorted()
    val shown = habits
        .filter {
            when (filter) {
                "above" -> it.percentage >= 85
                "below" -> it.percentage < 85
                else -> true
            }
        }
        .filter { habit -> tag?.let { it in habit.tags } ?: true }
        .let { list ->
            when (sort) {
                "desc" -> list.sortedByDescending { it.percentage }
                "asc" -> list.sortedBy { it.percentage }
                else -> list.sortedBy { it.name.lowercase() }
            }
        }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text("Habit Overview") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shift(false) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier") }
                    TextButton(onClick = { pickRange = true }, modifier = Modifier.weight(1f)) {
                        val (from, to) = range
                        val month = YearMonth.from(from)
                        Text(
                            if (from == month.atDay(1) && to == month.atEndOfMonth()) {
                                "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}"
                            } else {
                                "${from.shortLabel()} – ${to.shortLabel()}, ${to.year}"
                            },
                        )
                    }
                    IconButton(onClick = { vm.shift(true) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later") }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "All", "above" to "≥85%", "below" to "<85%").forEach { (v, l) ->
                        FilterChip(selected = filter == v, onClick = { filter = v }, label = { Text(l) })
                    }
                    listOf("desc" to "High → Low", "asc" to "Low → High", "name" to "A–Z").forEach { (v, l) ->
                        FilterChip(selected = sort == v, onClick = { sort = v }, label = { Text(l) })
                    }
                }
            }
            if (allTags.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = tag == null, onClick = { tag = null }, label = { Text("All tags") })
                        allTags.forEach { t ->
                            FilterChip(selected = tag == t, onClick = { tag = if (tag == t) null else t }, label = { Text(t) })
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        "No habits in this range yet. Any task with a Repeat rule set shows up here.",
                        modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { it.name }) { habit ->
                HabitCard(habit, onClick = { selected = habit }, onDelete = { toDelete = habit.name })
            }
        }
    }

    if (pickRange) {
        DateRangePickerDialog(range.first, range.second, onDismiss = { pickRange = false }, onPick = vm::setRange)
    }
    toDelete?.let { name ->
        DeleteRoutineDialog(
            name = name,
            onStop = { vm.stopRoutine(name); toDelete = null },
            onDeleteAll = { vm.deleteRoutine(name); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
    selected?.let { habit ->
        HabitDetailSheet(habit.name, repeating, YearMonth.from(range.second)) { selected = null }
    }
}

@Composable
private fun HabitCard(habit: HabitStats, onClick: () -> Unit, onDelete: () -> Unit) {
    val pct = habit.percentage.toInt()
    val color = StatusColors.forPercent(pct)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(habit.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("$pct%", color = color, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete routine",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (habit.percentage / 100).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = color,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "✓ ${habit.completed}  ✗ ${habit.missed}  ·  🔥 ${habit.currentStreak} (best ${habit.maxStreak})  ·  " +
                    "${habit.earned.pts()}/${habit.allocated.pts()} pts  ·  every ${habit.repeatLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Asks how to remove a routine: stop it (keep history) or delete every copy. */
@Composable
private fun DeleteRoutineDialog(name: String, onStop: () -> Unit, onDeleteAll: () -> Unit, onDismiss: () -> Unit) {
    var confirmAll by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirmAll) "Delete all of \"$name\"?" else "Remove \"$name\"") },
        text = {
            if (confirmAll) {
                Text("Every copy of this routine, past and upcoming, and their subtasks will be deleted. Its history and points will be gone. This can't be undone.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop routine") }
                    Text(
                        "Removes upcoming copies and stops repeating. Past days, stats and points are kept.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { confirmAll = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete everything") }
                    Text(
                        "Deletes every copy, including past days and their history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (confirmAll) {
                TextButton(onClick = onDeleteAll) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Calendar of done / missed days for one habit, one month at a time with ‹ › to move between months. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitDetailSheet(name: String, repeating: List<TaskEntity>, startMonth: YearMonth, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(startMonth) }
    val from = month.atDay(1)
    val to = month.atEndOfMonth()
    val stats = remember(repeating, name, month) {
        HabitStatsCalculator.compute(repeating.filter { it.title == name }, from, to).firstOrNull()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
                }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                }
            }
            Text(
                stats?.let {
                    "${it.percentage.toInt()}% · ${it.completed} done · ${it.missed} missed · best streak ${it.maxStreak}"
                } ?: "Nothing tracked this month",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            MonthGrid(month, stats?.days.orEmpty(), from, to)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, days: Map<LocalDate, Boolean>, from: LocalDate, to: LocalDate) {
    // Monday-first weeks.
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val day = week.getOrNull(i)
                val result = day?.let { days[it] }
                val bg = when {
                    day == null || day < from || day > to -> Color.Transparent
                    result == true -> StatusColors.Green.copy(alpha = 0.7f)
                    result == false -> StatusColors.Red.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    Modifier.weight(1f).aspectRatio(1f).padding(2.dp).background(bg, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (day != null) Text("${day.dayOfMonth}", fontSize = 11.sp)
                }
            }
        }
    }
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Legend(StatusColors.Green, "Done")
        Spacer(Modifier.width(12.dp))
        Legend(StatusColors.Red, "Missed")
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
    Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall)
}
