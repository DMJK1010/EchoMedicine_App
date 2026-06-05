package com.echomedicine.app.domain.model

data class Schedule(
    val slotNumber: Int,      // 0~2
    val medicineName: String, // 최대 15바이트 (UTF-8)
    val hour: Int,            // 0~23
    val minute: Int           // 0~59
)
