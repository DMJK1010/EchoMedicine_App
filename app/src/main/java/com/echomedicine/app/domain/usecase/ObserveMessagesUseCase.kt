package com.echomedicine.app.domain.usecase

import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.repository.BluetoothRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMessagesUseCase @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) {
    operator fun invoke(): Flow<BluetoothMessage> {
        return bluetoothRepository.incomingMessages
    }
}
