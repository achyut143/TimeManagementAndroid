package com.focusflow.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Status colors used across screens, matching the iOS green / orange / yellow / red scale. */
object StatusColors {
    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFFF9500)
    val Yellow = Color(0xFFFFCC00)
    val Red = Color(0xFFFF3B30)
    val Blue = Color(0xFF007AFF)
    val Purple = Color(0xFFAF52DE)

    /** Self-efficacy scale from the iOS Points dashboard. */
    fun forPercent(percent: Int): Color = when {
        percent >= 80 -> Green
        percent >= 60 -> Orange
        percent >= 40 -> Yellow
        else -> Red
    }

    fun forPriority(priority: String): Color = when (priority) {
        "P1" -> Red
        "P2" -> Orange
        "P3" -> Blue
        else -> Color.Gray
    }
}

@Composable
fun FocusFlowTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
