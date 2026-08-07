package com.justin.focuscheck.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {

    @Query(
        """
        SELECT * FROM check_ins
        ORDER BY createdAt DESC
        """
    )
    fun observeAllCheckIns(): Flow<List<CheckInEntity>>

    @Insert
    suspend fun insertCheckIn(
        checkIn: CheckInEntity
    ): Long
}