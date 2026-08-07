package com.justin.focuscheck.data

class TaskRepository(
    private val taskDao: TaskDao
) {

    val tasks = taskDao.observeAllTasks()

    suspend fun addTask(
        title: String,
        notes: String,
        importance: Int,
        urgency: Int,
        dueDateEpochDay: Long?
    ) {
        val task = TaskEntity(
            title = title.trim(),
            notes = notes.trim(),
            importance = importance.coerceIn(1, 5),
            urgency = urgency.coerceIn(1, 5),
            dueDateEpochDay = dueDateEpochDay
        )

        taskDao.insertTask(task)
    }

    suspend fun updateTask(
        taskId: Long,
        title: String,
        notes: String,
        importance: Int,
        urgency: Int,
        dueDateEpochDay: Long?
    ) {
        taskDao.updateTaskDetails(
            taskId = taskId,
            title = title.trim(),
            notes = notes.trim(),
            importance = importance.coerceIn(1, 5),
            urgency = urgency.coerceIn(1, 5),
            dueDateEpochDay = dueDateEpochDay
        )
    }

    suspend fun startTask(
        taskId: Long,
        taskTitle: String
    ) {
        taskDao.activateTask(
            taskId = taskId,
            reasonForPreviousTask = "Switched to $taskTitle",
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun pauseTask(
        taskId: Long,
        reason: String
    ) {
        taskDao.pauseTask(
            taskId = taskId,
            reason = reason.trim(),
            pausedAt = System.currentTimeMillis()
        )
    }

    suspend fun switchTask(
        currentTaskId: Long,
        nextTaskId: Long,
        nextTaskTitle: String,
        currentPauseReason: String
    ) {
        taskDao.pauseAndSwitchTask(
            currentTaskId = currentTaskId,
            nextTaskId = nextTaskId,
            currentPauseReason = currentPauseReason.trim(),
            reasonForOtherActiveTasks = "Switched to $nextTaskTitle",
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun updateStatus(
        taskId: Long,
        newStatus: String
    ) {
        taskDao.updateTaskStatus(
            taskId = taskId,
            newStatus = newStatus
        )
    }

    suspend fun deleteTask(task: TaskEntity) {
        taskDao.deleteTask(task)
    }
}