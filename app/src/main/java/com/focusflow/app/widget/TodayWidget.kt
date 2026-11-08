package com.focusflow.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.focusflow.app.FocusFlowApp
import com.focusflow.app.MainActivity
import com.focusflow.app.domain.PointsCalculator
import com.focusflow.app.domain.TaskTime
import com.focusflow.app.domain.isDone
import com.focusflow.app.domain.isUntimed
import java.time.LocalDate
import java.time.LocalTime

/** Home-screen widget: today's points and the next few pending tasks. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = (context.applicationContext as FocusFlowApp).container.db.taskDao()
        val today = dao.tasksOn(LocalDate.now())
        val points = PointsCalculator.of(today)
        val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
        val upcoming = today
            .filter { !it.isDone && !it.isUntimed && TaskTime.toMinutes(it.endTime) > nowMinutes }
            .sortedBy { TaskTime.toMinutes(it.startTime) }
            .take(3)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(16.dp)
                        .padding(12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                ) {
                    Text(
                        "Today · ${"%.0f".format(points.earned)}/${"%.0f".format(points.total)} pts (${points.percent}%)",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = GlanceTheme.colors.onSurface,
                        ),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    if (upcoming.isEmpty()) {
                        Text("Nothing else scheduled", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    }
                    upcoming.forEach { task ->
                        Text(
                            "${TaskTime.displayTime(task.startTime)}  ${task.title}",
                            maxLines = 1,
                            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface),
                        )
                    }
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
