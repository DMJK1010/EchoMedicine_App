package com.echomedicine.app.domain.model

data class MedicineSlot(
    val slotNumber: Int,
    val medicineName: String,
    val hour: Int,
    val minute: Int,
    val status: SlotStatus
)
