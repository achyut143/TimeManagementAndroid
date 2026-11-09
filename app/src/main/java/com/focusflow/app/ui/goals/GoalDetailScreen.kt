package com.focusflow.app.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusflow.app.AppContainer
import com.focusflow.app.data.GoalEntity
import com.focusflow.app.data.TaskEntity
import com.focusflow.app.domain.GoalItem
import com.focusflow.app.domain.GoalItems
import com.focusflow.app.domain.Progress
import com.focusflow.app.domain.TaskTime
import com.focusflow.app.domain.isUntimed
import com.focusflow.app.ui.common.NoInsets
import com.focusflow.app.ui.common.containerFactory
import com.focusflow.app.ui.common.longLabel
import com.focusflow.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class GoalDetailViewModel(c: AppContainer, goalId: String) : ViewModel() {
    val goal: StateFlow<GoalEntity?> = c.db.goalDao().observeById(goalId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The month shown, or null for all time. Opens on the current month. */
    private val _month = MutableStateFlow<YearMonth?>(YearMonth.now())
    val month: StateFlow<YearMonth?> = _month.asStateFlow()

    private val tasks = c.db.taskDao().observeForGoal(goalId)

    /** Tasks in the selected period. */
    val periodTasks: StateFlow<List<TaskEntity>> = combine(tasks, _month) { all, m ->
        if (m == null) all else all.filter { it.date != null && YearMonth.from(it.date) == m }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<GoalItem>> = combine(tasks, _month) { all, m ->
        GoalItems.build(all, m?.atDay(1), m?.atEndOfMonth())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shiftMonth(by: Long) { _month.value = (_month.value ?: YearMonth.now()).plusMonths(by) }
    fun setAllTime(all: Boolean) { _month.value = if (all) null else YearMonth.now() }
}

/** A goal's page: its progress, a month filter, and every task linked to it (repeat tasks collapsed per routine). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    goalId: String,
    onBack: () -> Unit,
    vm: GoalDetailViewModel = viewModel(key = "goal-$goalId", factory = containerFactory { GoalDetailViewModel(it, goalId) }),
) {
    val goal by vm.goal.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val periodTasks by vm.periodTasks.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val progress = Progress.of(periodTasks)

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(goal?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            goal?.goalDescription?.takeIf { it.isNotBlank() }?.let { desc ->
                item { Text(desc, style = MaterialTheme.typography.bodyMedium) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shiftMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                    Text(
                        month?.let { "${it.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${it.year}" } ?: "All time",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.shiftMonth(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
                    FilterChip(selected = month == null, onClick = { vm.setAllTime(month != null) }, label = { Text("All time") })
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Goal progress", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            ProgressPercent(progress, big = true)
                        }
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(progress)
                        ProgressSummary(progress, Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        if (month == null) "No tasks linked to this goal yet. Pick this goal in a task's editor."
                        else "No tasks for this goal in this month.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items, key = { it.key() }) { item ->
                when (item) {
                    is GoalItem.Routine -> RoutineCard(item)
                    is GoalItem.Single -> Card(Modifier.fillMaxWidth()) { TaskLine(item.task, showTitle = true) }
                }
            }
        }
    }
}

private fun GoalItem.key(): String = when (this) {
    is GoalItem.Routine -> "routine:$title"
    is GoalItem.Single -> task.id
}

/** One row per routine with its own percentage; tap to see every day. */
@Composable
private fun RoutineCard(routine: GoalItem.Routine) {
    var open by rememberSaveable(routine.title) { mutableStateOf(false) }
    val p = routine.progress
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.clickable { open = !open }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Repeat, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(routine.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(routine.repeatLabel?.let { "every $it" }, "${routine.tasks.size} days").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProgressPercent(p)
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Collapse" else "Expand")
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(p)
            ProgressSummary(p, Modifier.padding(top = 4.dp))
        }
        AnimatedVisibility(open) {
            Column {
                routine.tasks.forEach { task ->
                    HorizontalDivider()
                    TaskLine(task, showTitle = false)
                }
            }
        }
    }
}

@Composable
private fun TaskLine(task: TaskEntity, showTitle: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusIcon(task)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (showTitle) Text(task.title, fontWeight = FontWeight.Medium)
            val whenText = listOfNotNull(
                task.date?.longLabel() ?: "No date (Backlog)",
                if (!task.isUntimed) "${TaskTime.displayTime(task.startTime)} – ${TaskTime.displayTime(task.endTime)}" else null,
                task.repeatNumber?.let { "#$it" },
            ).joinToString(" · ")
            Text(
                whenText,
                style = if (showTitle) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = if (showTitle) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatusIcon(task: TaskEntity) = when {
    task.completed -> Icon(Icons.Filled.CheckCircle, "Done", tint = StatusColors.Green)
    task.notCompleted -> Icon(Icons.Filled.Cancel, "Missed", tint = StatusColors.Red)
    else -> Icon(Icons.Filled.RadioButtonUnchecked, "Pending", tint = MaterialTheme.colorScheme.outline)
}

// region Shared with the goal list

@Composable
fun ProgressPercent(p: Progress, big: Boolean = false) {
    val pct = p.percent
    Text(
        pct?.let { "$it%" } ?: "–",
        color = pct?.let { StatusColors.forPercent(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
    )
}

@Composable
fun ProgressBar(p: Progress) {
    val pct = p.percent ?: 0
    LinearProgressIndicator(
        progress = { pct / 100f },
        modifier = Modifier.fillMaxWidth(),
        color = StatusColors.forPercent(pct),
    )
}

@Composable
fun ProgressSummary(p: Progress, modifier: Modifier = Modifier) {
    Text(
        "✓ ${p.done} done  ✗ ${p.missed} missed  ·  ${p.pending} pending",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

// endregion
