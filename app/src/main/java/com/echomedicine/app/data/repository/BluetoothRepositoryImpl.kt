package com.echomedicine.app.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.echomedicine.app.data.bluetooth.BluetoothConnectionManager
import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.domain.repository.BluetoothRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BluetoothRepository의 구현체.
 *
 * BluetoothConnectionManager를 래핑하여 도메인 레이어에
 * 블루투스 통신 기능을 제공한다.
 *
 * @param connectionManager 블루투스 연결 관리자
 * @param bluetoothAdapter 시스템 BluetoothAdapter (BT 미지원 기기에서는 null)
 */
@Singleton
class BluetoothRepositoryImpl @Inject constructor(
    private val connectionManager: BluetoothConnectionManager,
    private val bluetoothAdapter: BluetoothAdapter?
) : BluetoothRepository {

    override val connectionState: StateFlow<ConnectionState>
        get() = connectionManager.connectionState

    override val incomingMessages: Flow<BluetoothMessage>
        get() = connectionManager.incomingMessages

    @SuppressLint("MissingPermission")
    override fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    override suspend fun connect(device: BluetoothDevice): Result<Unit> {
        return connectionManager.connect(device)
    }

    override suspend fun disconnect() {
        connectionManager.disconnect()
    }

    override suspend fun send(command: String): Result<Unit> {
        return connectionManager.send(command)
    }
}
