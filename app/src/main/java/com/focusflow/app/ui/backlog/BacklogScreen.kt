package com.focusflow.app.ui.backlog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusflow.app.AppContainer
import com.focusflow.app.data.BacklogFolderEntity
import com.focusflow.app.data.GoalEntity
import com.focusflow.app.data.TaskEntity
import com.focusflow.app.domain.FolderTree
import com.focusflow.app.ui.common.NoInsets
import com.focusflow.app.ui.common.containerFactory
import com.focusflow.app.ui.common.longLabel
import com.focusflow.app.ui.tasks.EditTaskSheet
import com.focusflow.app.ui.theme.StatusColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class BacklogViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository

    val tasks: StateFlow<List<TaskEntity>> = c.db.taskDao().observeBacklog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<BacklogFolderEntity>> = c.db.backlogFolderDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val goals: StateFlow<List<GoalEntity>> = c.db.goalDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTask(title: String, folderId: String?) = act { repo.addBacklogTask(title, folderId) }
    fun save(task: TaskEntity) = act { repo.save(task) }
    fun schedule(task: TaskEntity, day: LocalDate) = act { repo.scheduleFromBacklog(task, day) }
    fun delete(task: TaskEntity) = act { repo.delete(task) }
    fun moveTask(task: TaskEntity, folderId: String?) = act { repo.moveTaskToFolder(task, folderId) }

    fun createFolder(name: String, parentId: String?) = act { repo.createFolder(name, parentId) }
    fun renameFolder(folder: BacklogFolderEntity, name: String) = act { repo.renameFolder(folder, name) }
    fun moveFolder(folder: BacklogFolderEntity, parentId: String?) = act { repo.moveFolder(folder, parentId) }
    fun deleteFolder(folder: BacklogFolderEntity) = act { repo.deleteFolder(folder) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = containerFactory { BacklogViewModel(it) }
    }
}

/** One choice in the "move to…" picker: a folder, or null for Unfiled / Top level. */
private data class PickerOption(val folderId: String?, val label: String)

private sealed interface FolderDialog {
    data class NewFolder(val parent: BacklogFolderEntity?) : FolderDialog
    data class Rename(val folder: BacklogFolderEntity) : FolderDialog
    data class MoveFolder(val folder: BacklogFolderEntity) : FolderDialog
    data class DeleteFolder(val folder: BacklogFolderEntity) : FolderDialog
    data class MoveTask(val task: TaskEntity) : FolderDialog
}

/**
 * Android version of the iOS BacklogView: quick-add, folder chips
 * (All · Unfiled · folders, which can nest), and unscheduled tasks.
 * Long-press a folder chip for New Folder Inside / Rename / Move / Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen(onBack: () -> Unit, vm: BacklogViewModel = viewModel(factory = BacklogViewModel.Factory)) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var unfiledOnly by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }

    // If the open folder was deleted, fall back to the top level.
    val selected = folders.firstOrNull { it.id == selectedId }
    if (selectedId != null && selected == null && folders.isNotEmpty()) selectedId = null

    // A folder shows its own tasks plus those in every folder inside it.
    val shown = when {
        unfiledOnly -> tasks.filter { it.backlogFolderId == null || folders.none { f -> f.id == it.backlogFolderId } }
        selected == null -> tasks
        else -> {
            val ids = FolderTree.descendants(selected.id, folders) + selected.id
            tasks.filter { it.backlogFolderId in ids }
        }
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Backlog") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            QuickAdd(onAdd = { vm.addTask(it, if (unfiledOnly) null else selected?.id) })

            FolderChips(
                folders = folders,
                selected = selected,
                unfiledOnly = unfiledOnly,
                onSelect = { folder, unfiled -> selectedId = folder?.id; unfiledOnly = unfiled },
                onNewFolder = { dialog = FolderDialog.NewFolder(selected) },
                onFolderAction = { dialog = it },
            )

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (shown.isEmpty()) {
                    item { EmptyState(if (tasks.isEmpty()) "No backlog tasks yet" else "No tasks in this folder") }
                }
                items(shown, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        // Where it's filed, when that isn't obvious from the chips.
                        folderPath = folders.firstOrNull { it.id == task.backlogFolderId }
                            ?.takeIf { it.id != selected?.id }
                            ?.let { FolderTree.path(it, folders) },
                        onClick = { editing = task },
                        onSchedule = { day -> vm.schedule(task, day) },
                        onMove = { dialog = FolderDialog.MoveTask(task) },
                        onDelete = { vm.delete(task) },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        is FolderDialog.NewFolder -> NameDialog(
            title = d.parent?.let { "New folder in ${it.name}" } ?: "New folder",
            initial = "",
            confirm = "Create",
            onDismiss = { dialog = null },
            onConfirm = { vm.createFolder(it, d.parent?.id); dialog = null },
        )
        is FolderDialog.Rename -> NameDialog(
            title = "Rename folder",
            initial = d.folder.name,
            confirm = "Save",
            onDismiss = { dialog = null },
            onConfirm = { vm.renameFolder(d.folder, it); dialog = null },
        )
        is FolderDialog.MoveFolder -> {
            val options = buildList {
                if (d.folder.parentId != null) add(PickerOption(null, "Top level"))
                FolderTree.sortedByPath(FolderTree.moveTargets(d.folder, folders))
                    .filter { (f, _) -> f.id != d.folder.parentId }
                    .forEach { (f, path) -> add(PickerOption(f.id, path)) }
            }
            PickerDialog("Move \"${d.folder.name}\" to", options, onDismiss = { dialog = null }) {
                vm.moveFolder(d.folder, it)
                dialog = null
            }
        }
        is FolderDialog.MoveTask -> {
            val options = listOf(PickerOption(null, "Unfiled")) +
                FolderTree.sortedByPath(folders).map { (f, path) -> PickerOption(f.id, path) }
            PickerDialog("Move \"${d.task.title}\" to", options, onDismiss = { dialog = null }) {
                vm.moveTask(d.task, it)
                dialog = null
            }
        }
        is FolderDialog.DeleteFolder -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete folder?") },
            text = {
                Text(
                    if (d.folder.parentId == null) {
                        "Tasks and folders inside move back to the top level (tasks to Unfiled). Nothing is deleted."
                    } else {
                        "Tasks and folders inside move up into the folder above. Nothing is deleted."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { vm.deleteFolder(d.folder); dialog = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        null -> {}
    }

    editing?.let { task ->
        EditTaskSheet(
            initial = task,
            isNew = false,
            goals = goals,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
        )
    }
}

@Composable
private fun QuickAdd(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add a backlog task…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = { submit() }, enabled = text.isNotBlank()) { Icon(Icons.Filled.Add, "Add task") }
    }
}

/** Top level: All · Unfiled · top folders. Inside a folder: ‹ back, the folder itself, then the folders inside it. */
@Composable
private fun FolderChips(
    folders: List<BacklogFolderEntity>,
    selected: BacklogFolderEntity?,
    unfiledOnly: Boolean,
    onSelect: (BacklogFolderEntity?, Boolean) -> Unit,
    onNewFolder: () -> Unit,
    onFolderAction: (FolderDialog) -> Unit,
) {
    fun hasChildren(f: BacklogFolderEntity) = FolderTree.children(f.id, folders).isNotEmpty()

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        if (selected != null) {
            item {
                Chip(selected = false, onClick = {
                    onSelect(FolderTree.chain(selected.parentId, folders).firstOrNull(), false)
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", Modifier.size(18.dp))
                }
            }
            item {
                FolderChip(FolderTree.path(selected, folders), selected = true, hasChildren = false, folder = selected,
                    onClick = {}, onFolderAction = onFolderAction)
            }
            items(FolderTree.children(selected.id, folders), key = { it.id }) { sub ->
                FolderChip(sub.name, selected = false, hasChildren = hasChildren(sub), folder = sub,
                    onClick = { onSelect(sub, false) }, onFolderAction = onFolderAction)
            }
        } else {
            item { Chip(selected = !unfiledOnly, onClick = { onSelect(null, false) }) { Text("All") } }
            item { Chip(selected = unfiledOnly, onClick = { onSelect(null, true) }) { Text("Unfiled") } }
            items(FolderTree.children(null, folders), key = { it.id }) { folder ->
                FolderChip(folder.name, selected = false, hasChildren = hasChildren(folder), folder = folder,
                    onClick = { onSelect(folder, false) }, onFolderAction = onFolderAction)
            }
        }
        item {
            Chip(selected = false, onClick = onNewFolder) {
                Icon(Icons.Filled.CreateNewFolder, if (selected == null) "New folder" else "New folder inside", Modifier.size(18.dp))
            }
        }
    }
}

/** A folder chip; long-press opens its menu (New Folder Inside, Rename, Move, Delete). */
@Composable
private fun FolderChip(
    name: String,
    selected: Boolean,
    hasChildren: Boolean,
    folder: BacklogFolderEntity,
    onClick: () -> Unit,
    onFolderAction: (FolderDialog) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Chip(selected = selected, onClick = onClick, onLongClick = { menu = true }) {
            Icon(Icons.Filled.Folder, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(name, maxLines = 1)
            if (hasChildren) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Has folders inside", Modifier.size(16.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("New folder inside") },
                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                onClick = { menu = false; onFolderAction(FolderDialog.NewFolder(folder)) },
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                onClick = { menu = false; onFolderAction(FolderDialog.Rename(folder)) },
            )
            DropdownMenuItem(
                text = { Text("Move folder") },
                leadingIcon = { Icon(Icons.Filled.DriveFileMove, null) },
                onClick = { menu = false; onFolderAction(FolderDialog.MoveFolder(folder)) },
            )
            DropdownMenuItem(
                text = { Text("Delete folder", color = StatusColors.Red) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = StatusColors.Red) },
                onClick = { menu = false; onFolderAction(FolderDialog.DeleteFolder(folder)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.heightIn(min = 36.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    folderPath: String?,
    onClick: () -> Unit,
    onSchedule: (LocalDate) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Medium)
                if (task.tags.isNotBlank()) {
                    Text(task.tags, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                folderPath?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Folder, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            PriorityPill(task.priority)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Task actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Schedule for today") },
                        leadingIcon = { Icon(Icons.Filled.EventAvailable, null) },
                        onClick = { menu = false; onSchedule(today) },
                    )
                    DropdownMenuItem(
                        text = { Text("Schedule for tomorrow (${today.plusDays(1).longLabel()})") },
                        leadingIcon = { Icon(Icons.Filled.EventAvailable, null) },
                        onClick = { menu = false; onSchedule(today.plusDays(1)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to folder") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileMove, null) },
                        onClick = { menu = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = StatusColors.Red) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = StatusColors.Red) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityPill(priority: String) {
    Text(
        priority,
        modifier = Modifier.background(StatusColors.forPriority(priority), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun EmptyState(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Inbox, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickerDialog(title: String, options: List<PickerOption>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (options.isEmpty()) {
                Text("There are no other folders to move to.")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options) { option ->
                        Text(
                            option.label,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onPick(option.folderId) }
                                .padding(vertical = 12.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
