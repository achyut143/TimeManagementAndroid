package com.focusflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Android version of the iOS `@AppStorage` settings. */
class SettingsStore(private val context: Context) {
    private val metricDaysKey = intPreferencesKey("metricDays")
    private val percentageFilterKey = stringPreferencesKey("habitPercentageFilter")

    /** How many days the completion rate and streak on task cards look back (7, 15, 30, 45 or 60). */
    val metricDays: Flow<Int> = context.dataStore.data.map { it[metricDaysKey] ?: 30 }

    /** "all", "above" (>= 85%) or "below" (< 85%). Filters repeat tasks on the Tasks screen. */
    val percentageFilter: Flow<String> = context.dataStore.data.map { it[percentageFilterKey] ?: "all" }

    suspend fun setMetricDays(days: Int) {
        context.dataStore.edit { it[metricDaysKey] = days }
    }

    suspend fun setPercentageFilter(value: String) {
        context.dataStore.edit { it[percentageFilterKey] = value }
    }
}
