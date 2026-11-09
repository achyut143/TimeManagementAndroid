package com.focusflow.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.app.data.TaskEntity
import com.focusflow.app.domain.TaskMetrics
import com.focusflow.app.domain.TaskTime
import com.focusflow.app.domain.isRunning
import com.focusflow.app.domain.isRepeating
import com.focusflow.app.ui.theme.StatusColors
import java.time.LocalTime
import kotlin.math.roundToInt

/** Height of one minute on the timeline: an hour is 120dp, a 15-minute task is 30dp. */
val MinuteHeight: Dp = 2.dp
val HourHeight: Dp = MinuteHeight * 60
private val LabelWidth = 64.dp
private val MinCardHeight = 20.dp

/** Below this height a task shows as a single compact line. */
private val CompactBelow = 64.dp

/** Below this width (overlapping tasks side by side) cards drop the time range or stack their content. */
private val NarrowBelow = 220.dp

/**
 * A day timeline where each task sits at its start minute and is as tall as
 * its duration. Tasks that overlap share the width side by side, like iOS.
 * Tapping an empty spot starts a new task at that time (rounded to 15 min).
 * Long-press a task and drag it up or down to move it (snaps to 5 min).
 */
@Composable
fun Timeline(
    tasks: List<TaskEntity>,
    metrics: Map<String, TaskMetrics>,
    isToday: Boolean,
    now: LocalTime,
    onTapEmpty: (startMinute: Int) -> Unit,
    onSelect: (TaskEntity) -> Unit,
    onMove: (TaskEntity, startMinute: Int) -> Unit,
    onComplete: (TaskEntity) -> Unit,
    onNotComplete: (TaskEntity) -> Unit,
) {
    val columns = remember(tasks) { layoutColumns(tasks) }
    val tapEmpty by rememberUpdatedState(onTapEmpty)
    val move by rememberUpdatedState(onMove)
    val minutePx = with(LocalDensity.current) { MinuteHeight.toPx() }
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(HourHeight * 24).pointerInput(Unit) {
            detectTapGestures { offset ->
                val minute = (offset.y / MinuteHeight.toPx()).toInt().coerceIn(0, 24 * 60 - 15)
                tapEmpty(minute - minute % 15)
            }
        },
    ) {
        // Hour lines and labels, plus a faint half-hour line.
        for (hour in 0 until 24) {
            Box(Modifier.offset(y = HourHeight * hour).fillMaxWidth()) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    TaskTime.displayTime(TaskTime.fromMinutes(hour * 60)),
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(
                modifier = Modifier.offset(x = LabelWidth, y = HourHeight * hour + MinuteHeight * 30),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }

        val trackWidth = maxWidth - LabelWidth - 12.dp
        tasks.forEach { task ->
            val start = TaskTime.toMinutes(task.startTime)
            // A task running past midnight is drawn up to the end of the day.
            val duration = TaskTime.span(task.startTime, task.endTime).coerceAtMost(24 * 60 - start)
            val (column, count) = columns[task.id] ?: (0 to 1)
            val width = trackWidth / count
            val height = (MinuteHeight * duration).coerceAtLeast(MinCardHeight)
            var dragging by remember(task.id) { mutableStateOf(false) }
            var dragY by remember(task.id) { mutableFloatStateOf(0f) }
            // Where the task would start if dropped now, snapped to 5 minutes.
            val dropStart = if (dragging) {
                val delta = ((dragY / minutePx) / 5).roundToInt() * 5
                (start + delta).coerceIn(0, 24 * 60 - 5)
            } else {
                start
            }
            Box(
                Modifier.zIndex(if (dragging) 1f else 0f)
                    .offset(x = LabelWidth + width * column, y = MinuteHeight * dropStart)
                    .width(width).height(height).padding(horizontal = 1.dp, vertical = 0.5.dp)
                    .then(if (dragging) Modifier.shadow(8.dp, RoundedCornerShape(8.dp)) else Modifier)
                    .pointerInput(task.id, task.startTime) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragY = 0f
                                dragging = true
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragY += amount.y
                            },
                            onDragEnd = {
                                val delta = ((dragY / minutePx) / 5).roundToInt() * 5
                                val newStart = (start + delta).coerceIn(0, 24 * 60 - 5)
                                dragging = false
                                dragY = 0f
                                if (newStart != start) move(task, newStart)
                            },
                            onDragCancel = {
                                dragging = false
                                dragY = 0f
                            },
                        )
                    },
            ) {
                val isCurrent = isToday && task.isRunning()
                if (height < CompactBelow) {
                    CompactTaskCard(task, isCurrent, showTime = width >= NarrowBelow, onSelect, onComplete, onNotComplete)
                } else if (width < NarrowBelow) {
                    NarrowTaskCard(task, isCurrent, onSelect, onComplete, onNotComplete)
                } else {
                    TaskCard(
                        task = task,
                        metrics = metrics[task.id],
                        isCurrent = isCurrent,
                        onClick = { onSelect(task) },
                        onComplete = { onComplete(task) },
                        onNotComplete = { onNotComplete(task) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (dragging) {
                    Text(
                        TaskTime.displayTime(TaskTime.fromMinutes(dropStart)),
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                            .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (isToday) {
            val y = MinuteHeight * (now.hour * 60 + now.minute)
            Box(Modifier.offset(x = LabelWidth - 4.dp, y = y - 4.dp).size(8.dp).background(StatusColors.Red, CircleShape))
            HorizontalDivider(Modifier.offset(x = LabelWidth, y = y - 1.dp), thickness = 2.dp, color = StatusColors.Red)
        }
    }
}

/** One-line card for short tasks: title, start time and small done / missed buttons. */
@Composable
private fun CompactTaskCard(
    task: TaskEntity,
    isCurrent: Boolean,
    showTime: Boolean,
    onSelect: (TaskEntity) -> Unit,
    onComplete: (TaskEntity) -> Unit,
    onNotComplete: (TaskEntity) -> Unit,
) {
    Row(
        Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(containerColor(task, isCurrent))
            .clickable { onSelect(task) }.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(StatusColors.forPriority(task.priority), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            task.title,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (task.completed) TextDecoration.LineThrough else null,
        )
        repeatBadge(task)?.let {
            Text(
                it,
                fontSize = 11.sp,
                maxLines = 1,
                color = if (task.reassign) StatusColors.Red else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        if (showTime) {
            Text(
                "${TaskTime.displayTime(task.startTime)}–${TaskTime.displayTime(task.endTime)}",
                fontSize = 11.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
        DoneButtons(task, onComplete, onNotComplete)
    }
}

/** Tall but narrow card (an overlapping task): title on top, then time, then small buttons. */
@Composable
private fun NarrowTaskCard(
    task: TaskEntity,
    isCurrent: Boolean,
    onSelect: (TaskEntity) -> Unit,
    onComplete: (TaskEntity) -> Unit,
    onNotComplete: (TaskEntity) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(containerColor(task, isCurrent))
            .clickable { onSelect(task) }.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(StatusColors.forPriority(task.priority), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                task.title,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
            )
        }
        Text(
            "${TaskTime.displayTime(task.startTime)}–${TaskTime.displayTime(task.endTime)}" +
                (repeatBadge(task)?.let { "  $it" } ?: ""),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) { DoneButtons(task, onComplete, onNotComplete) }
    }
}

/** "↻#3" for a repeat task (just "↻" if it has no number yet), null otherwise. */
private fun repeatBadge(task: TaskEntity): String? =
    if (!task.isRepeating) null else "↻" + (task.repeatNumber?.let { "#$it" } ?: "")

@Composable
private fun containerColor(task: TaskEntity, isCurrent: Boolean) = when {
    task.completed -> StatusColors.Green.copy(alpha = 0.25f)
    task.notCompleted -> StatusColors.Red.copy(alpha = 0.2f)
    isCurrent -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun DoneButtons(task: TaskEntity, onComplete: (TaskEntity) -> Unit, onNotComplete: (TaskEntity) -> Unit) {
    Icon(
        Icons.Filled.Check,
        contentDescription = "Completed",
        modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onComplete(task) },
        tint = if (task.completed) StatusColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(8.dp))
    Icon(
        Icons.Filled.Close,
        contentDescription = "Not completed",
        modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onNotComplete(task) },
        tint = if (task.notCompleted) StatusColors.Red else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Side-by-side columns for overlapping tasks: task id -> (column, column count).
 * Tasks are grouped into clusters that overlap each other; within a cluster each
 * task takes the first column that is free at its start time.
 */
internal fun layoutColumns(tasks: List<TaskEntity>): Map<String, Pair<Int, Int>> {
    val result = mutableMapOf<String, Pair<Int, Int>>()
    val sorted = tasks.sortedBy { TaskTime.toMinutes(it.startTime) }
    var cluster = mutableListOf<Pair<String, Int>>() // task id to column
    var columnEnds = mutableListOf<Int>()
    var clusterEnd = -1

    fun closeCluster() {
        cluster.forEach { (id, col) -> result[id] = col to columnEnds.size }
        cluster = mutableListOf()
        columnEnds = mutableListOf()
    }

    for (task in sorted) {
        val start = TaskTime.toMinutes(task.startTime)
        val end = start + TaskTime.span(task.startTime, task.endTime)
        if (start >= clusterEnd) closeCluster()
        val free = columnEnds.indexOfFirst { it <= start }
        val col = if (free >= 0) free else columnEnds.size
        if (col == columnEnds.size) columnEnds.add(end) else columnEnds[col] = end
        cluster.add(task.id to col)
        clusterEnd = maxOf(clusterEnd, end)
    }
    closeCluster()
    return result
}
