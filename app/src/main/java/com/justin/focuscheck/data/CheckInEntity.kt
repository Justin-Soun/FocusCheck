package com.justin.focuscheck.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val createdAt: Long = System.currentTimeMillis(),

    val taskId: Long? = null,

    val taskTitle: String,

    val reportedStatus: String,

    val note: String = "",

    val nextTaskId: Long? = null,

    val nextTaskTitle: String? = null
)