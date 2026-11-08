package com.focusflow.app.ui.points

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusflow.app.AppContainer
import com.focusflow.app.domain.Points
import com.focusflow.app.domain.PointsCalculator
import com.focusflow.app.ui.common.DateRangePickerDialog
import com.focusflow.app.ui.common.NoInsets
import com.focusflow.app.ui.common.containerFactory
import com.focusflow.app.ui.common.pts
import com.focusflow.app.ui.common.shortLabel
import com.focusflow.app.ui.theme.StatusColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class PointsViewModel(c: AppContainer) : ViewModel() {
    // Last 30 days, like iOS.
    private val _range = MutableStateFlow(LocalDate.now().minusDays(30) to LocalDate.now())
    val range: StateFlow<Pair<LocalDate, LocalDate>> = _range.asStateFlow()

    val byDay: StateFlow<Map<LocalDate, Points>> = _range
        .flatMapLatest { (from, to) -> c.db.taskDao().observeBetween(from, to) }
        .map { PointsCalculator.byDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setRange(from: LocalDate, to: LocalDate) { _range.value = from to to }

    fun lastDays(days: Long) { _range.value = LocalDate.now().minusDays(days) to LocalDate.now() }

    /** Moves the whole range earlier or later by its own length (iOS DateRangeShiftControl). */
    fun shift(forward: Boolean) {
        val (from, to) = _range.value
        val days = to.toEpochDay() - from.toEpochDay() + 1
        val step = if (forward) days else -days
        _range.value = from.plusDays(step) to to.plusDays(step)
    }

    companion object {
        val Factory = containerFactory { PointsViewModel(it) }
    }
}

/** Android version of the iOS Self-Efficacy (Points) dashboard. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PointsScreen(vm: PointsViewModel = viewModel(factory = PointsViewModel.Factory)) {
    val range by vm.range.collectAsStateWithLifecycle()
    val byDay by vm.byDay.collectAsStateWithLifecycle()
    var pickRange by remember { mutableStateOf(false) }

    val days = generateSequence(range.first) { it.plusDays(1) }.takeWhile { !it.isAfter(range.second) }.toList()
    val total = Points(byDay.values.sumOf { it.earned }, byDay.values.sumOf { it.total })

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text("Self-Efficacy") }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.shift(false) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier") }
                        TextButton(onClick = { pickRange = true }, modifier = Modifier.weight(1f)) {
                            Text("${range.first.shortLabel()} – ${range.second.shortLabel()}, ${range.second.year}")
                        }
                        IconButton(onClick = { vm.shift(true) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7L, 30L, 90L).forEach { d ->
                            FilterChip(selected = false, onClick = { vm.lastDays(d) }, label = { Text("Last $d days") })
                        }
                    }
                    Text(
                        "Total ${total.earned.pts()} / ${total.total.pts()} pts · ${total.percent}%",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = StatusColors.forPercent(total.percent),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            items(days, key = { it.toEpochDay() }) { day -> DayCell(day, byDay[day] ?: Points(0.0, 0.0)) }
        }
    }

    if (pickRange) {
        DateRangePickerDialog(range.first, range.second, onDismiss = { pickRange = false }, onPick = vm::setRange)
    }
}

@Composable
private fun DayCell(day: LocalDate, points: Points) {
    val pct = points.percent
    val color = StatusColors.forPercent(pct)
    Column(
        Modifier.fillMaxWidth()
            .background(color.copy(alpha = if (pct >= 100) 0.3f else 0.2f), RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${day.dayOfMonth}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(day.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 9.sp)
        Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 9.sp)
        Text("${points.earned.toInt()}/${points.total.toInt()}", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Text("$pct%", fontSize = 9.sp, color = color)
    }
}
