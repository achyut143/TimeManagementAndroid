package com.focusflow.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusflow.app.data.TaskEntity
import com.focusflow.app.domain.Points
import com.focusflow.app.domain.QuickTaskParser
import com.focusflow.app.domain.TaskMetrics
import com.focusflow.app.domain.TaskTime
import com.focusflow.app.domain.isRunning
import com.focusflow.app.domain.isUntimed
import com.focusflow.app.focus.FocusState
import com.focusflow.app.focus.FocusTimer
import com.focusflow.app.ui.common.DatePickerDialogFor
import com.focusflow.app.ui.common.NoInsets
import com.focusflow.app.ui.common.longLabel
import com.focusflow.app.ui.common.pts
import com.focusflow.app.ui.theme.StatusColors
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onOpenBacklog: () -> Unit,
    vm: TasksViewModel = viewModel(factory = TasksViewModel.Factory),
) {
    val date by vm.date.collectAsStateWithLifecycle()
    val allTasks by vm.tasks.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val focus by vm.focus.collectAsStateWithLifecycle()
    val metricDays by vm.metricDays.collectAsStateWithLifecycle()
    val percentageFilter by vm.percentageFilter.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMetricSettings by remember { mutableStateOf(false) }
    var confirmMarkAll by remember { mutableStateOf(false) }

    // Same filter as iOS: only repeat tasks are filtered by their completion rate.
    val tasks = allTasks.filter { task ->
        val rate = metrics[task.id]?.completionRate ?: return@filter true
        when (percentageFilter) {
            "above" -> rate >= 85
            "below" -> rate < 85
            else -> true
        }
    }
    val untimed = tasks.filter { it.isUntimed }.sortedBy { it.title.lowercase() }
    val timed = tasks.filterNot { it.isUntimed }

    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalTime.now()
        }
    }
    val isToday = date == LocalDate.now()

    fun newTask(startMinute: Int? = null) {
        val start = startMinute?.let { LocalTime.of(it / 60, it % 60) } ?: TaskTime.roundedNow()
        editing = TaskEntity(
            date = date,
            startTime = TaskTime.fromLocalTime(start),
            endTime = TaskTime.fromLocalTime(start.plusMinutes(15)),
        )
        editingIsNew = true
    }

    // Scroll the timeline to an hour before now (or to 7 AM on other days).
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val headerItems = 3 + (if (focus.isActive) 1 else 0) + (if (untimed.isNotEmpty()) 1 + untimed.size else 0)
    LaunchedEffect(date, allTasks.isNotEmpty()) {
        val hour = if (isToday) (LocalTime.now().hour - 1).coerceAtLeast(0) else 7
        val offset = with(density) { (HourHeight * hour).roundToPx() }
        listState.scrollToItem(headerItems, offset)
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.shiftDate(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day")
                        }
                        Text(
                            if (isToday) "Today" else date.longLabel(),
                            modifier = Modifier.clickable { showDatePicker = true },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { vm.shiftDate(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day")
                        }
                    }
                },
                actions = {
                    if (!isToday) TextButton(onClick = { vm.setDate(LocalDate.now()) }) { Text("Today") }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Backlog") },
                            onClick = { showMenu = false; onOpenBacklog() },
                        )
                        DropdownMenuItem(
                            text = { Text("Metrics: last $metricDays days") },
                            onClick = { showMenu = false; showMetricSettings = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Mark all not completed") },
                            onClick = { showMenu = false; confirmMarkAll = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newTask() }) { Icon(Icons.Filled.Add, "Add task") }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { PointsHeader(points) }
            item { QuickAddBar(onAdd = vm::quickAdd) }
            item { Spacer(Modifier.size(4.dp)) }
            if (focus.isActive) {
                item {
                    FocusBanner(focus, onPause = vm::pauseFocus, onResume = vm::resumeFocus, onStop = vm::stopFocus)
                }
            }
            if (untimed.isNotEmpty()) {
                item { SectionLabel("Untimed") }
                items(untimed, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        metrics = metrics[task.id],
                        isCurrent = false,
                        onClick = { selectedId = task.id },
                        onComplete = { vm.toggleCompleted(task) },
                        onNotComplete = { vm.toggleNotCompleted(task) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item(key = "timeline") {
                Timeline(
                    tasks = timed,
                    metrics = metrics,
                    isToday = isToday,
                    now = now,
                    onTapEmpty = { newTask(it) },
                    onSelect = { selectedId = it.id },
                    onComplete = vm::toggleCompleted,
                    onNotComplete = vm::toggleNotCompleted,
                )
            }
        }
    }

    selectedId?.let { id ->
        TaskDetailSheet(
            taskId = id,
            vm = vm,
            goals = goals,
            onEdit = { editing = it; editingIsNew = false },
            onDismiss = { selectedId = null },
        )
    }

    editing?.let { task ->
        EditTaskSheet(
            initial = task,
            isNew = editingIsNew,
            goals = goals,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
        )
    }

    if (showDatePicker) {
        DatePickerDialogFor(date, onDismiss = { showDatePicker = false }, onPick = vm::setDate)
    }

    if (confirmMarkAll) {
        AlertDialog(
            onDismissRequest = { confirmMarkAll = false },
            title = { Text("Mark all not completed?") },
            text = { Text("Every pending task on this day will be marked not completed and rolled over.") },
            confirmButton = {
                TextButton(onClick = { vm.markAllNotCompleted(); confirmMarkAll = false }) { Text("Mark All") }
            },
            dismissButton = { TextButton(onClick = { confirmMarkAll = false }) { Text("Cancel") } },
        )
    }

    if (showMetricSettings) {
        MetricSettingsDialog(
            days = metricDays,
            filter = percentageFilter,
            onDays = vm::setMetricDays,
            onFilter = vm::setPercentageFilter,
            onDismiss = { showMetricSettings = false },
        )
    }
}

@Composable
private fun PointsHeader(points: Points) {
    val color = StatusColors.forPercent(points.percent)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Points", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            "${points.earned.pts()} / ${points.total.pts()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("${points.percent}%", color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickAddBar(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("15:30 - 16:30 - task - 2 - r1") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = { showHelp = true }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, "Format help") }
            },
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = ::submit) { Icon(Icons.Filled.Add, "Add") }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Quick add") },
            text = { Text(QuickTaskParser.HELP_TEXT) },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun FocusBanner(focus: FocusState, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
    val remaining by produceState(focus.remainingMs(), focus) {
        while (true) {
            value = focus.remainingMs()
            delay(1_000)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, null)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(focus.label.ifBlank { "Focus" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    FocusTimer.formatRemaining(remaining) + if (focus.isPaused) " · paused" else "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (focus.isRunning) {
                IconButton(onClick = onPause) { Icon(Icons.Filled.Pause, "Pause") }
            } else {
                IconButton(onClick = onResume) { Icon(Icons.Filled.PlayArrow, "Resume") }
            }
            IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, "Stop") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun TaskCard(
    task: TaskEntity,
    metrics: TaskMetrics?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onNotComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when {
        task.completed -> StatusColors.Green.copy(alpha = 0.15f)
        task.notCompleted -> StatusColors.Red.copy(alpha = 0.12f)
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(StatusColors.forPriority(task.priority), CircleShape))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!task.isUntimed) {
                        MetaText("${TaskTime.displayTime(task.startTime)} – ${TaskTime.displayTime(task.endTime)}")
                    }
                    MetaText("${task.weight.pts()} pt")
                    task.repeatAgain?.let { days ->
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = "Repeats",
                            modifier = Modifier.size(14.dp),
                            // Red = next occurrence already handled, as on iOS.
                            tint = if (task.reassign) StatusColors.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MetaText("${days}d")
                    }
                    metrics?.let {
                        MetaText(
                            "${it.completionRate.toInt()}% · 🔥${it.currentStreak}",
                            color = StatusColors.forPercent(it.completionRate.toInt()),
                        )
                    }
                }
                if (task.tags.isNotBlank()) MetaText(task.tags)
            }
            IconButton(
                onClick = onComplete,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (task.completed) StatusColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) { Icon(Icons.Filled.Check, "Completed") }
            IconButton(
                onClick = onNotComplete,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (task.notCompleted) StatusColors.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) { Icon(Icons.Filled.Close, "Not completed") }
        }
    }
}

@Composable
private fun MetaText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricSettingsDialog(
    days: Int,
    filter: String,
    onDays: (Int) -> Unit,
    onFilter: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metrics period") },
        text = {
            Column {
                Text("Show completion rate and streak for:", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7, 15, 30, 45, 60).forEach { d ->
                        FilterChip(selected = d == days, onClick = { onDays(d) }, label = { Text("${d}d") })
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text("Show repeat tasks matching:", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "All", "above" to "≥85%", "below" to "<85%").forEach { (value, label) ->
                        FilterChip(selected = value == filter, onClick = { onFilter(value) }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
