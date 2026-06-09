package com.echomedicine.app.domain.model

sealed class BluetoothMessage {
    data class MedicineAlert(val medicineName: String) : BluetoothMessage()
    data class TakenConfirmation(val medicineName: String) : BluetoothMessage()
    data class SettingConfirmation(val medicineName: String, val time: String) : BluetoothMessage()
    data class RealertWarning(val medicineName: String) : BluetoothMessage()
    object DailyReset : BluetoothMessage()
    data class ScheduleInfo(
        val slot: Int,
        val name: String,
        val hour: Int,
        val minute: Int,
        val isDone: Boolean,
        val present: Boolean = false
    ) : BluetoothMessage()

    /** 약통 약 유무 알림 (📦). present=true 약 있음, false 비어있음 */
    data class MedicinePresence(val slot: Int, val medicineName: String, val present: Boolean) : BluetoothMessage()

    data class Unknown(val raw: String) : BluetoothMessage()
}
