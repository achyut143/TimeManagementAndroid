package com.focusflow.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.app.AppContainer
import com.focusflow.app.data.GoalEntity
import com.focusflow.app.data.SubtaskEntity
import com.focusflow.app.data.TaskEntity
import com.focusflow.app.domain.Points
import com.focusflow.app.domain.PointsCalculator
import com.focusflow.app.domain.TaskMetrics
import com.focusflow.app.domain.TaskMetricsCalculator
import com.focusflow.app.domain.allocatedMinutes
import com.focusflow.app.domain.endDateTime
import com.focusflow.app.domain.isRepeating
import com.focusflow.app.domain.isRunning
import com.focusflow.app.focus.FocusState
import com.focusflow.app.ui.common.containerFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val dao = c.db.taskDao()

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    init {
        // Each time a day is opened, create the repeat tasks that belong on it.
        viewModelScope.launch { _date.collect { repo.ensureRepeatsFor(it) } }
    }

    val tasks: StateFlow<List<TaskEntity>> = _date
        .flatMapLatest { dao.observeOn(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val points: StateFlow<Points> = tasks.map { PointsCalculator.of(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Points(0.0, 0.0))

    val metricDays: StateFlow<Int> = c.settings.metricDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)

    val percentageFilter: StateFlow<String> = c.settings.percentageFilter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "all")

    /** Completion rate and streak for each repeat task on the selected day, keyed by task id. */
    val metrics: StateFlow<Map<String, TaskMetrics>> =
        combine(tasks, dao.observeRepeating(), metricDays, _date) { day, repeating, days, ref ->
            day.filter { it.isRepeating }
                .associate { it.id to TaskMetricsCalculator.calculate(it.title, repeating, days, ref) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val goals: StateFlow<List<GoalEntity>> = c.db.goalDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val focus: StateFlow<FocusState> = c.focusTimer.state

    fun observeTask(id: String): Flow<TaskEntity?> = dao.observeById(id)
    fun observeSubtasks(taskId: String): Flow<List<SubtaskEntity>> = c.db.subtaskDao().observeForTask(taskId)

    fun setDate(day: LocalDate) { _date.value = day }
    fun shiftDate(days: Long) { _date.value = _date.value.plusDays(days) }

    fun setMetricDays(days: Int) = act { c.settings.setMetricDays(days) }
    fun setPercentageFilter(value: String) = act { c.settings.setPercentageFilter(value) }

    fun save(task: TaskEntity) = act { repo.save(task) }
    fun delete(task: TaskEntity) = act { repo.delete(task) }
    fun toggleCompleted(task: TaskEntity) = act { repo.toggleCompleted(task) }
    fun toggleNotCompleted(task: TaskEntity) = act { repo.toggleNotCompleted(task) }
    fun markAllNotCompleted() = act { repo.markAllNotCompleted(_date.value) }
    fun snooze(task: TaskEntity, minutes: Int) = act { repo.snooze(task, minutes) }
    fun moveTask(task: TaskEntity, startMinute: Int) = act { repo.moveTo(task, startMinute) }
    fun sendToBacklog(task: TaskEntity) = act { repo.sendToBacklog(task) }
    fun quickAdd(input: String) = act { repo.quickAdd(input, _date.value) }

    fun addSubtask(taskId: String, name: String) = act { repo.addSubtask(taskId, name) }
    fun toggleSubtask(s: SubtaskEntity) = act { repo.toggleSubtask(s) }
    fun deleteSubtask(s: SubtaskEntity) = act { repo.deleteSubtask(s) }
    fun moveSubtask(s: SubtaskEntity, step: Int) = act { repo.moveSubtask(s, step) }

    /** Starts the focus timer: until the task's end if it's running now, else its full length (25 min fallback). */
    fun startFocus(task: TaskEntity) {
        val now = LocalDateTime.now()
        val minutes = when {
            task.isRunning(now) -> Duration.between(now, task.endDateTime()!!).toMinutes().toInt() + 1
            task.allocatedMinutes > 0 -> task.allocatedMinutes.toInt()
            else -> 25
        }
        c.focusTimer.start(task.title, minutes, task.id)
    }

    fun pauseFocus() = c.focusTimer.pause()
    fun resumeFocus() = c.focusTimer.resume()
    fun stopFocus() = c.focusTimer.stop()

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = containerFactory { TasksViewModel(it) }
    }
}
