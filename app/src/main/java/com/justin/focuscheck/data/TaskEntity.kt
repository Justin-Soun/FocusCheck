package com.justin.focuscheck.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val notes: String = "",

    val importance: Int,

    val urgency: Int,

    /*
     * A date is stored as a calendar-day number rather
     * than a timestamp, preventing time-zone shifts.
     */
    val dueDateEpochDay: Long? = null,

    val status: String = TaskStatus.NOT_STARTED,

    val pauseReason: String? = null,

    val pausedAt: Long? = null,

    val createdAt: Long = System.currentTimeMillis()
) {
    fun priorityScore(
        today: LocalDate = LocalDate.now()
    ): Int {
        val baseScore =
            importance * 2 + urgency * 3

        val dueDateBonus =
            dueDateEpochDay?.let { dueDay ->
                val daysUntilDue =
                    dueDay - today.toEpochDay()

                when {
                    daysUntilDue < 0L -> 10
                    daysUntilDue == 0L -> 8
                    daysUntilDue == 1L -> 5
                    daysUntilDue <= 7L -> 2
                    else -> 0
                }
            } ?: 0

        return baseScore + dueDateBonus
    }
}

object TaskStatus {
    const val NOT_STARTED = "NOT_STARTED"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val BLOCKED = "BLOCKED"
}