package com.justin.focuscheck.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justin.focuscheck.data.CheckInEntity
import com.justin.focuscheck.data.CheckInRepository
import com.justin.focuscheck.data.FocusCheckDatabase
import com.justin.focuscheck.data.TaskEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CheckInViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository =
        CheckInRepository(
            FocusCheckDatabase.getDatabase(application)
        )

    val checkIns: StateFlow<List<CheckInEntity>> =
        repository.checkIns.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000
            ),
            initialValue = emptyList()
        )

    fun submitCheckIn(
        task: TaskEntity,
        reportedStatus: String,
        note: String,
        nextTask: TaskEntity?
    ) {
        viewModelScope.launch {
            repository.submitCheckIn(
                task = task,
                reportedStatus = reportedStatus,
                note = note,
                nextTask = nextTask
            )
        }
    }
}