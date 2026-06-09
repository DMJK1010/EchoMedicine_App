package com.echomedicine.app.domain.model

data class MedicineSlot(
    val slotNumber: Int,
    val medicineName: String,
    val hour: Int,
    val minute: Int,
    val status: SlotStatus,
    val present: Boolean = true   // 약통에 약이 들어있는지 여부 (IR 센서)
)
