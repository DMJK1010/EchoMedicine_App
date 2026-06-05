package com.echomedicine.app.domain.usecase

import android.bluetooth.BluetoothDevice
import com.echomedicine.app.domain.repository.BluetoothRepository
import javax.inject.Inject

class ConnectDeviceUseCase @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) {
    suspend operator fun invoke(device: BluetoothDevice): Result<Unit> {
        return bluetoothRepository.connect(device)
    }
}
