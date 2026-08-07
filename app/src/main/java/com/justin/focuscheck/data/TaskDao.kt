package com.justin.focuscheck.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query(
        """
        SELECT * FROM tasks
        ORDER BY
            CASE
                WHEN status = 'IN_PROGRESS' THEN 0
                WHEN status = 'NOT_STARTED' THEN 1
                WHEN status = 'PAUSED' THEN 2
                WHEN status = 'BLOCKED' THEN 3
                WHEN status = 'COMPLETED' THEN 4
                ELSE 5
            END ASC,
            (importance * 2 + urgency * 3) DESC,
            createdAt ASC
        """
    )
    fun observeAllTasks(): Flow<List<TaskEntity>>
    @Query(
        """
    SELECT COUNT(*)
    FROM tasks
    WHERE status != 'COMPLETED'
    """
    )
    suspend fun countUnfinishedTasks(): Int

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Query(
        """
    UPDATE tasks
    SET
        title = :title,
        notes = :notes,
        importance = :importance,
        urgency = :urgency,
        dueDateEpochDay = :dueDateEpochDay
    WHERE id = :taskId
    """
    )
    suspend fun updateTaskDetails(
        taskId: Long,
        title: String,
        notes: String,
        importance: Int,
        urgency: Int,
        dueDateEpochDay: Long?
    )

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query(
        """
        UPDATE tasks
        SET
            status = :newStatus,
            pauseReason = NULL,
            pausedAt = NULL
        WHERE id = :taskId
        """
    )
    suspend fun updateTaskStatus(
        taskId: Long,
        newStatus: String
    )

    @Query(
        """
        UPDATE tasks
        SET
            status = 'PAUSED',
            pauseReason = :reason,
            pausedAt = :pausedAt
        WHERE id = :taskId
        """
    )
    suspend fun pauseTask(
        taskId: Long,
        reason: String,
        pausedAt: Long
    )
    @Query(
        """
    UPDATE tasks
    SET
        status = 'BLOCKED',
        pauseReason = :reason,
        pausedAt = :blockedAt
    WHERE id = :taskId
    """
    )
    suspend fun blockTask(
        taskId: Long,
        reason: String,
        blockedAt: Long
    )
    @Query(
        """
        UPDATE tasks
        SET
            status = 'PAUSED',
            pauseReason = :reason,
            pausedAt = :pausedAt
        WHERE status = 'IN_PROGRESS'
            AND id != :exceptTaskId
        """
    )
    suspend fun pauseOtherInProgressTasks(
        exceptTaskId: Long,
        reason: String,
        pausedAt: Long
    )

    @Query(
        """
        UPDATE tasks
        SET
            status = 'IN_PROGRESS',
            pauseReason = NULL,
            pausedAt = NULL
        WHERE id = :taskId
        """
    )
    suspend fun setTaskInProgress(taskId: Long)

    @Transaction
    suspend fun activateTask(
        taskId: Long,
        reasonForPreviousTask: String,
        timestamp: Long
    ) {
        pauseOtherInProgressTasks(
            exceptTaskId = taskId,
            reason = reasonForPreviousTask,
            pausedAt = timestamp
        )

        setTaskInProgress(taskId)
    }

    @Transaction
    suspend fun pauseAndSwitchTask(
        currentTaskId: Long,
        nextTaskId: Long,
        currentPauseReason: String,
        reasonForOtherActiveTasks: String,
        timestamp: Long
    ) {
        pauseOtherInProgressTasks(
            exceptTaskId = nextTaskId,
            reason = reasonForOtherActiveTasks,
            pausedAt = timestamp
        )

        pauseTask(
            taskId = currentTaskId,
            reason = currentPauseReason,
            pausedAt = timestamp
        )

        setTaskInProgress(nextTaskId)
    }
}