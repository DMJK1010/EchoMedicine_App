package com.echomedicine.app.domain.model

sealed class BluetoothMessage {
    data class MedicineAlert(val medicineName: String) : BluetoothMessage()
    data class TakenConfirmation(val medicineName: String) : BluetoothMessage()
    data class SettingConfirmation(val medicineName: String, val time: String) : BluetoothMessage()
    data class RealertWarning(val medicineName: String) : BluetoothMessage()
    object DailyReset : BluetoothMessage()
    data class ScheduleInfo(val slot: Int, val name: String, val hour: Int, val minute: Int, val isDone: Boolean) : BluetoothMessage()
    data class Unknown(val raw: String) : BluetoothMessage()
}
