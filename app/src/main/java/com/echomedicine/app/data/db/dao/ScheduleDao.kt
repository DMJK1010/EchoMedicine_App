package com.echomedicine.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.echomedicine.app.data.db.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY slotNumber")
    fun getAllSchedules(): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(schedules: List<ScheduleEntity>)

    @Query("SELECT * FROM schedules WHERE slotNumber = :slot")
    suspend fun getBySlot(slot: Int): ScheduleEntity?

    @Query("SELECT MAX(lastSyncedAt) FROM schedules")
    suspend fun getLastSyncTime(): Long?
}
