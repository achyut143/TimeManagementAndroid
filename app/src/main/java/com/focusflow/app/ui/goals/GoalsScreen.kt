package com.focusflow.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusflow.app.AppContainer
import com.focusflow.app.data.GoalEntity
import com.focusflow.app.ui.common.NoInsets
import com.focusflow.app.ui.common.containerFactory
import com.focusflow.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalRow(val goal: GoalEntity, val taskCount: Int, val completed: Int) {
    val percent: Int get() = if (taskCount > 0) completed * 100 / taskCount else 0
}

class GoalsViewModel(private val c: AppContainer) : ViewModel() {
    val goals: StateFlow<List<GoalRow>> =
        combine(c.db.goalDao().observeAll(), c.db.taskDao().observeWithGoal()) { goals, tasks ->
            val byGoal = tasks.groupBy { it.goalId }
            goals.map { g ->
                val list = byGoal[g.id].orEmpty()
                GoalRow(g, list.size, list.count { it.completed })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(goal: GoalEntity) { viewModelScope.launch { c.repository.saveGoal(goal) } }
    fun delete(goal: GoalEntity) { viewModelScope.launch { c.repository.deleteGoal(goal) } }

    companion object {
        val Factory = containerFactory { GoalsViewModel(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(vm: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory)) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    var deleting by remember { mutableStateOf<GoalEntity?>(null) }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text("Goals") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = GoalEntity(name = "") }) { Icon(Icons.Filled.Add, "Add goal") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (goals.isEmpty()) {
                item {
                    Text(
                        "No goals yet. Add one, then link tasks to it from the task editor.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(goals, key = { it.goal.id }) { row ->
                Card(onClick = { editing = row.goal }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.goal.name, fontWeight = FontWeight.SemiBold)
                                if (row.goal.goalDescription.isNotBlank()) {
                                    Text(row.goal.goalDescription, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { deleting = row.goal }) { Icon(Icons.Filled.Delete, "Delete goal") }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { row.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = StatusColors.forPercent(row.percent),
                        )
                        Text(
                            "${row.completed}/${row.taskCount} tasks done · ${row.percent}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }

    editing?.let { goal ->
        var name by remember(goal.id) { mutableStateOf(goal.name) }
        var description by remember(goal.id) { mutableStateOf(goal.goalDescription) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (goal.name.isEmpty()) "New goal" else "Edit goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("Description") })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.save(goal.copy(name = name.trim(), goalDescription = description.trim()))
                        editing = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${goal.name}\"?") },
            text = { Text("Tasks linked to it are kept; they just lose the goal.") },
            confirmButton = { TextButton(onClick = { vm.delete(goal); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
