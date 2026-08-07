package com.justin.focuscheck.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository =
        SettingsRepository(application)

    val settings: StateFlow<ReminderSettings> =
        repository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000
            ),
            initialValue = ReminderSettings()
        )

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRemindersEnabled(enabled)
        }
    }

    fun setStartTime(
        hour: Int,
        minute: Int
    ) {
        viewModelScope.launch {
            repository.setStartTime(
                hour = hour,
                minute = minute
            )
        }
    }

    fun setEndTime(
        hour: Int,
        minute: Int
    ) {
        viewModelScope.launch {
            repository.setEndTime(
                hour = hour,
                minute = minute
            )
        }
    }

    fun setCheckInInterval(minutes: Int) {
        viewModelScope.launch {
            repository.setCheckInInterval(minutes)
        }
    }

    fun setMissedReminderInterval(minutes: Int) {
        viewModelScope.launch {
            repository.setMissedReminderInterval(minutes)
        }
    }

    fun setActiveDay(
        dayNumber: Int,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            repository.setActiveDay(
                dayNumber = dayNumber,
                enabled = enabled
            )
        }
    }
}