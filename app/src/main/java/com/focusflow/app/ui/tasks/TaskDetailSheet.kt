package com.focusflow.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.focusflow.app.data.GoalEntity
import com.focusflow.app.data.TaskEntity
import com.focusflow.app.domain.TaskTime
import com.focusflow.app.domain.isUntimed
import com.focusflow.app.ui.common.longLabel
import com.focusflow.app.ui.common.pts
import com.focusflow.app.ui.theme.StatusColors

/** Android version of the iOS TaskActionsView. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailSheet(
    taskId: String,
    vm: TasksViewModel,
    goals: List<GoalEntity>,
    onEdit: (TaskEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val flow = remember(taskId) { vm.observeTask(taskId) }
    val subtaskFlow = remember(taskId) { vm.observeSubtasks(taskId) }
    val task by flow.collectAsState(initial = null)
    val subtasks by subtaskFlow.collectAsState(initial = emptyList())
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newSubtask by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Close if the task disappears (deleted), but not before it has first loaded.
    LaunchedEffect(task) {
        if (task != null) loaded = true else if (loaded) onDismiss()
    }
    val t = task ?: return

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding(),
        ) {
            Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            val details = buildList {
                t.date?.let { add(it.longLabel()) }
                if (!t.isUntimed) add("${TaskTime.displayTime(t.startTime)} – ${TaskTime.displayTime(t.endTime)}")
                add("${t.weight.pts()} pt")
                add(t.priority)
                t.repeatAgain?.let { add("repeats every $it day${if (it == 1) "" else "s"}") }
                t.goalId?.let { id -> goals.firstOrNull { it.id == id }?.let { add("goal: ${it.name}") } }
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            if (t.tags.isNotBlank()) Text("Tags: ${t.tags}", style = MaterialTheme.typography.bodySmall)
            t.timeSpent?.let { Text("Time spent: ${it.toInt()} min", style = MaterialTheme.typography.bodySmall) }
            if (!t.info.isNullOrBlank()) {
                Spacer(Modifier.size(8.dp))
                Text(t.info, style = MaterialTheme.typography.bodyMedium)
            }
            if (!t.notes.isNullOrBlank()) {
                Spacer(Modifier.size(8.dp))
                Text("Notes", style = MaterialTheme.typography.labelLarge)
                Text(t.notes, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.size(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { vm.toggleCompleted(t) },
                    label = { Text(if (t.completed) "Completed ✓" else "Complete") },
                    leadingIcon = { Icon(Icons.Filled.Check, null, tint = StatusColors.Green) },
                )
                AssistChip(
                    onClick = { vm.toggleNotCompleted(t) },
                    label = { Text(if (t.notCompleted) "Not completed ✓" else "Not completed") },
                    leadingIcon = { Icon(Icons.Filled.Close, null, tint = StatusColors.Red) },
                )
                AssistChip(
                    onClick = { vm.startFocus(t) },
                    label = { Text("Start focus") },
                    leadingIcon = { Icon(Icons.Filled.Timer, null) },
                )
                AssistChip(
                    onClick = { onEdit(t) },
                    label = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                )
                AssistChip(
                    onClick = { vm.sendToBacklog(t); onDismiss() },
                    label = { Text("To backlog") },
                    leadingIcon = { Icon(Icons.Filled.Inventory2, null) },
                )
                AssistChip(
                    onClick = { confirmDelete = true },
                    label = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = StatusColors.Red) },
                )
            }

            if (!t.isUntimed && !t.completed && !t.notCompleted) {
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Snooze, null, Modifier.size(18.dp))
                    Text(" Snooze", style = MaterialTheme.typography.labelLarge)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 60).forEach { mins ->
                        AssistChip(onClick = { vm.snooze(t, mins) }, label = { Text("+$mins min") })
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            HorizontalDivider()
            Spacer(Modifier.size(8.dp))
            Text("Subtasks", style = MaterialTheme.typography.titleMedium)
            val topLevel = subtasks.filter { it.parentId == null }
            topLevel.forEachIndexed { index, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s.completed, onCheckedChange = { vm.toggleSubtask(s) })
                    Text(
                        s.name,
                        modifier = Modifier.weight(1f),
                        textDecoration = if (s.completed) TextDecoration.LineThrough else null,
                    )
                    IconButton(onClick = { vm.moveSubtask(s, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                    }
                    IconButton(onClick = { vm.moveSubtask(s, 1) }, enabled = index < topLevel.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                    }
                    IconButton(onClick = { vm.deleteSubtask(s) }) { Icon(Icons.Filled.Delete, "Delete subtask") }
                }
                // Nested subtasks (created on iOS or copied from a repeat) are shown indented.
                subtasks.filter { it.parentId == s.id }.forEach { child ->
                    Row(Modifier.padding(start = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = child.completed, onCheckedChange = { vm.toggleSubtask(child) })
                        Text(child.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.deleteSubtask(child) }) { Icon(Icons.Filled.Delete, "Delete subtask") }
                    }
                }
            }
            fun addSubtask() {
                if (newSubtask.isNotBlank()) {
                    vm.addSubtask(t.id, newSubtask)
                    newSubtask = ""
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSubtask,
                    onValueChange = { newSubtask = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a subtask") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addSubtask() }),
                )
                IconButton(onClick = { addSubtask() }) { Icon(Icons.Filled.Add, "Add subtask") }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete task?") },
            text = { Text("\"${t.title}\" and its subtasks will be deleted.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(t); onDismiss() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
