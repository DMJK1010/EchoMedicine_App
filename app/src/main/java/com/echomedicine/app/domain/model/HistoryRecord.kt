package com.echomedicine.app.domain.model

data class HistoryRecord(
    val id: Long = 0,
    val slotNumber: Int,
    val medicineName: String,
    val scheduledHour: Int,
    val scheduledMinute: Int,
    val actualTakenTime: Long?,  // null이면 미복용
    val date: Long,              // 날짜 (yyyy-MM-dd 기준 millis)
    val status: SlotStatus       // TAKEN 또는 MISSED
)
