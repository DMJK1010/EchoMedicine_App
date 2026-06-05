package com.echomedicine.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotNumber: Int,
    val medicineName: String,
    val scheduledHour: Int,
    val scheduledMinute: Int,
    val actualTakenTime: Long?,  // null이면 미복용
    val date: Long,              // 날짜 (yyyy-MM-dd 기준 millis)
    val status: String           // "TAKEN" | "MISSED"
)
