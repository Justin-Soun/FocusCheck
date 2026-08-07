package com.justin.focuscheck.data

import androidx.room.withTransaction

class CheckInRepository(
    private val database: FocusCheckDatabase
) {

    private val taskDao = database.taskDao()
    private val checkInDao = database.checkInDao()

    val checkIns = checkInDao.observeAllCheckIns()

    suspend fun submitCheckIn(
        task: TaskEntity,
        reportedStatus: String,
        note: String,
        nextTask: TaskEntity?
    ) {
        val timestamp = System.currentTimeMillis()
        val cleanedNote = note.trim()

        val validNextTask = nextTask?.takeIf {
            it.id != task.id &&
                    it.status != TaskStatus.COMPLETED
        }

        /*
         * A task cannot remain in progress while another task
         * becomes active. Selecting a next task means the user
         * is switching immediately.
         */
        val finalCurrentStatus =
            if (
                validNextTask != null &&
                reportedStatus == TaskStatus.IN_PROGRESS
            ) {
                TaskStatus.PAUSED
            } else {
                reportedStatus
            }

        val finalNote =
            if (
                validNextTask != null &&
                finalCurrentStatus == TaskStatus.PAUSED
            ) {
                cleanedNote.ifBlank {
                    "Switched to ${validNextTask.title}"
                }
            } else {
                cleanedNote
            }

        database.withTransaction {
            when (finalCurrentStatus) {
                TaskStatus.IN_PROGRESS -> {
                    taskDao.pauseOtherInProgressTasks(
                        exceptTaskId = task.id,
                        reason =
                            "Switched during check-in to ${task.title}",
                        pausedAt = timestamp
                    )

                    taskDao.setTaskInProgress(
                        taskId = task.id
                    )
                }

                TaskStatus.COMPLETED -> {
                    taskDao.updateTaskStatus(
                        taskId = task.id,
                        newStatus = TaskStatus.COMPLETED
                    )
                }

                TaskStatus.PAUSED -> {
                    taskDao.pauseTask(
                        taskId = task.id,
                        reason = finalNote.ifBlank {
                            "Paused during check-in"
                        },
                        pausedAt = timestamp
                    )
                }

                TaskStatus.BLOCKED -> {
                    taskDao.blockTask(
                        taskId = task.id,
                        reason = finalNote.ifBlank {
                            "Blocked during check-in"
                        },
                        blockedAt = timestamp
                    )
                }
            }

            /*
             * Selecting another task always switches to it now.
             */
            if (validNextTask != null) {
                taskDao.pauseOtherInProgressTasks(
                    exceptTaskId = validNextTask.id,
                    reason =
                        "Switched during check-in to ${validNextTask.title}",
                    pausedAt = timestamp
                )

                taskDao.setTaskInProgress(
                    taskId = validNextTask.id
                )
            }

            checkInDao.insertCheckIn(
                CheckInEntity(
                    createdAt = timestamp,
                    taskId = task.id,
                    taskTitle = task.title,
                    reportedStatus = finalCurrentStatus,
                    note = finalNote,
                    nextTaskId = validNextTask?.id,
                    nextTaskTitle = validNextTask?.title
                )
            )
        }
    }
}