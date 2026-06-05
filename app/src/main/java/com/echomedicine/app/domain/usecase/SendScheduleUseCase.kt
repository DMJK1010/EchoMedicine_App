package com.echomedicine.app.domain.usecase

import com.echomedicine.app.domain.model.Schedule
import com.echomedicine.app.domain.repository.BluetoothRepository
import javax.inject.Inject

class SendScheduleUseCase @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) {
    suspend operator fun invoke(schedule: Schedule): Result<Unit> {
        val command = "SET:${schedule.slotNumber}:${schedule.medicineName}:${schedule.hour}:${schedule.minute}\n"
        return bluetoothRepository.send(command)
    }
}
