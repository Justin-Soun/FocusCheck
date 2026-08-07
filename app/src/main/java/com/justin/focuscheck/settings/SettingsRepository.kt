package com.justin.focuscheck.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.reminderSettingsDataStore by preferencesDataStore(
    name = "reminder_settings"
)

class SettingsRepository(
    private val context: Context
) {

    private object Keys {
        val REMINDERS_ENABLED =
            booleanPreferencesKey("reminders_enabled")

        val START_HOUR =
            intPreferencesKey("start_hour")

        val START_MINUTE =
            intPreferencesKey("start_minute")

        val END_HOUR =
            intPreferencesKey("end_hour")

        val END_MINUTE =
            intPreferencesKey("end_minute")

        val CHECK_IN_INTERVAL =
            intPreferencesKey("check_in_interval_minutes")

        val MISSED_REMINDER_INTERVAL =
            intPreferencesKey("missed_reminder_minutes")

        val ACTIVE_DAYS =
            stringSetPreferencesKey("active_days")
    }

    val settings: Flow<ReminderSettings> =
        context.reminderSettingsDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences.toReminderSettings()
            }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.reminderSettingsDataStore.edit { preferences ->
            preferences[Keys.REMINDERS_ENABLED] = enabled
        }
    }

    suspend fun setStartTime(
        hour: Int,
        minute: Int
    ) {
        context.reminderSettingsDataStore.edit { preferences ->
            preferences[Keys.START_HOUR] =
                hour.coerceIn(0, 23)

            preferences[Keys.START_MINUTE] =
                minute.coerceIn(0, 59)
        }
    }

    suspend fun setEndTime(
        hour: Int,
        minute: Int
    ) {
        context.reminderSettingsDataStore.edit { preferences ->
            preferences[Keys.END_HOUR] =
                hour.coerceIn(0, 23)

            preferences[Keys.END_MINUTE] =
                minute.coerceIn(0, 59)
        }
    }

    suspend fun setCheckInInterval(minutes: Int) {
        context.reminderSettingsDataStore.edit { preferences ->
            preferences[Keys.CHECK_IN_INTERVAL] =
                minutes.coerceIn(15, 240)
        }
    }

    suspend fun setMissedReminderInterval(minutes: Int) {
        context.reminderSettingsDataStore.edit { preferences ->
            preferences[Keys.MISSED_REMINDER_INTERVAL] =
                minutes.coerceIn(10, 60)
        }
    }

    suspend fun setActiveDay(
        dayNumber: Int,
        enabled: Boolean
    ) {
        if (dayNumber !in 1..7) {
            return
        }

        context.reminderSettingsDataStore.edit { preferences ->
            val storedDays =
                preferences[Keys.ACTIVE_DAYS]

            val currentDays =
                if (storedDays == null) {
                    ReminderSettings.DEFAULT_ACTIVE_DAYS
                        .map(Int::toString)
                        .toMutableSet()
                } else {
                    storedDays.toMutableSet()
                }

            if (enabled) {
                currentDays.add(dayNumber.toString())
            } else {
                currentDays.remove(dayNumber.toString())
            }

            preferences[Keys.ACTIVE_DAYS] = currentDays
        }
    }

    private fun Preferences.toReminderSettings():
            ReminderSettings {

        val storedDays = this[Keys.ACTIVE_DAYS]

        val activeDays =
            if (storedDays == null) {
                ReminderSettings.DEFAULT_ACTIVE_DAYS
            } else {
                storedDays
                    .mapNotNull(String::toIntOrNull)
                    .filter { it in 1..7 }
                    .toSet()
            }

        return ReminderSettings(
            remindersEnabled =
                this[Keys.REMINDERS_ENABLED] ?: false,

            startHour =
                this[Keys.START_HOUR] ?: 8,

            startMinute =
                this[Keys.START_MINUTE] ?: 0,

            endHour =
                this[Keys.END_HOUR] ?: 18,

            endMinute =
                this[Keys.END_MINUTE] ?: 0,

            checkInIntervalMinutes =
                this[Keys.CHECK_IN_INTERVAL] ?: 30,

            missedReminderMinutes =
                (
                        this[Keys.MISSED_REMINDER_INTERVAL]
                            ?: 10
                        ).coerceIn(10, 60),
            activeDays = activeDays
        )
    }
}