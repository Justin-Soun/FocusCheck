package com.justin.focuscheck.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.justin.focuscheck.data.FocusCheckDatabase
import com.justin.focuscheck.data.TaskEntity
import com.justin.focuscheck.data.TaskRepository
import com.justin.focuscheck.data.TaskStatus
import com.justin.focuscheck.notifications.ReminderCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val applicationContext =
        application.applicationContext

    private val repository: TaskRepository

    val tasks: StateFlow<List<TaskEntity>>

    init {
        val taskDao = FocusCheckDatabase
            .getDatabase(application)
            .taskDao()

        repository = TaskRepository(taskDao)

        tasks = repository.tasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000
            ),
            initialValue = emptyList()
        )
    }

    fun addTask(
        title: String,
        notes: String,
        importance: Int,
        urgency: Int,
        dueDateEpochDay: Long?
    ) {
        if (title.isBlank()) {
            return
        }

        viewModelScope.launch {
            repository.addTask(
                title = title,
                notes = notes,
                importance = importance,
                urgency = urgency,
                dueDateEpochDay = dueDateEpochDay
            )

            ReminderCoordinator.reconcile(
                context = applicationContext,
                reason = "a task was added"
            )
        }
    }

    fun updateTask(
        task: TaskEntity,
        title: String,
        notes: String,
        importance: Int,
        urgency: Int,
        dueDateEpochDay: Long?
    ) {
        if (title.isBlank()) {
            return
        }

        viewModelScope.launch {
            repository.updateTask(
                taskId = task.id,
                title = title,
                notes = notes,
                importance = importance,
                urgency = urgency,
                dueDateEpochDay = dueDateEpochDay
            )
        }
    }

    fun markInProgress(task: TaskEntity) {
        viewModelScope.launch {
            repository.startTask(
                taskId = task.id,
                taskTitle = task.title
            )
        }
    }

    fun pauseTask(
        task: TaskEntity,
        reason: String
    ) {
        if (reason.isBlank()) {
            return
        }

        viewModelScope.launch {
            repository.pauseTask(
                taskId = task.id,
                reason = reason
            )
        }
    }

    fun switchTask(
        currentTask: TaskEntity,
        nextTask: TaskEntity,
        pauseReason: String
    ) {
        if (currentTask.id == nextTask.id) {
            return
        }

        viewModelScope.launch {
            repository.switchTask(
                currentTaskId = currentTask.id,
                nextTaskId = nextTask.id,
                nextTaskTitle = nextTask.title,
                currentPauseReason = pauseReason
            )
        }
    }

    fun markCompleted(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateStatus(
                taskId = task.id,
                newStatus = TaskStatus.COMPLETED
            )

            ReminderCoordinator.reconcile(
                context = applicationContext,
                reason = "a task was completed"
            )
        }
    }

    fun reopenTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateStatus(
                taskId = task.id,
                newStatus = TaskStatus.NOT_STARTED
            )

            ReminderCoordinator.reconcile(
                context = applicationContext,
                reason = "a task was reopened"
            )
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)

            ReminderCoordinator.reconcile(
                context = applicationContext,
                reason = "a task was deleted"
            )
        }
    }
}
