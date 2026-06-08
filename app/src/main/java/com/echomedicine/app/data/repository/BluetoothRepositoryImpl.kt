package com.echomedicine.app.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.echomedicine.app.data.bluetooth.BluetoothConnectionManager
import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.domain.repository.BluetoothRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BluetoothRepository의 구현체.
 *
 * BluetoothConnectionManager를 래핑하여 도메인 레이어에
 * 블루투스 통신 기능을 제공한다. 또한 주변 기기 검색(discovery)을 지원한다.
 *
 * @param connectionManager 블루투스 연결 관리자
 * @param bluetoothAdapter 시스템 BluetoothAdapter (BT 미지원 기기에서는 null)
 * @param context 애플리케이션 컨텍스트 (BroadcastReceiver 등록용)
 */
@Singleton
class BluetoothRepositoryImpl @Inject constructor(
    private val connectionManager: BluetoothConnectionManager,
    private val bluetoothAdapter: BluetoothAdapter?,
    @ApplicationContext private val context: Context
) : BluetoothRepository {

    companion object {
        private const val TAG = "BluetoothRepository"
    }

    override val connectionState: StateFlow<ConnectionState>
        get() = connectionManager.connectionState

    override val incomingMessages: Flow<BluetoothMessage>
        get() = connectionManager.incomingMessages

    @SuppressLint("MissingPermission")
    override fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * 주변 블루투스 기기 검색을 시작하고, 발견되는 기기를 Flow로 발행한다.
     *
     * BluetoothDevice.ACTION_FOUND 브로드캐스트를 callbackFlow로 래핑한다.
     * Flow 수집이 취소되면 자동으로 검색을 중지하고 리시버를 해제한다.
     */
    @SuppressLint("MissingPermission")
    override fun startDiscovery(): Flow<BluetoothDevice> = callbackFlow {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            close()
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                        device?.let { trySend(it) }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "Discovery finished")
                        close()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)

        // 진행 중인 검색이 있으면 취소 후 새로 시작
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        val started = adapter.startDiscovery()
        if (!started) {
            Log.w(TAG, "Failed to start discovery")
            close()
        }

        awaitClose {
            try {
                adapter.cancelDiscovery()
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver already unregistered: ${e.message}")
            }
        }
    }

    /**
     * 진행 중인 블루투스 기기 검색을 중지한다.
     */
    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot cancel discovery: ${e.message}")
        }
    }

    override suspend fun connect(device: BluetoothDevice): Result<Unit> {
        // 연결 전 검색 중지 (검색 중에는 연결 성능이 저하됨)
        stopDiscovery()
        return connectionManager.connect(device)
    }

    override suspend fun disconnect() {
        connectionManager.disconnect()
    }

    override suspend fun send(command: String): Result<Unit> {
        return connectionManager.send(command)
    }
}
