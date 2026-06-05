package com.echomedicine.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val slotNumber: Int,
    val medicineName: String,
    val hour: Int,
    val minute: Int,
    val lastSyncedAt: Long  // Unix timestamp (millis)
)
