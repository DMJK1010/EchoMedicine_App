package com.echomedicine.app.domain.repository

import android.bluetooth.BluetoothDevice
import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BluetoothRepository {
    val connectionState: StateFlow<ConnectionState>
    val incomingMessages: Flow<BluetoothMessage>

    fun getPairedDevices(): List<BluetoothDevice>
    suspend fun connect(device: BluetoothDevice): Result<Unit>
    suspend fun disconnect()
    suspend fun send(command: String): Result<Unit>
}
