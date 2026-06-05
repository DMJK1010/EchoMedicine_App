package com.echomedicine.app.data.bluetooth

import android.bluetooth.BluetoothDevice
import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.ConnectionState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 블루투스 연결의 전체 생명주기를 관리하는 인터페이스.
 *
 * SPP UUID(00001101-0000-1000-8000-00805F9B34FB)를 사용하여
 * Bluetooth Classic 소켓 연결을 수립하고, 메시지 송수신 및
 * 자동 재연결 로직을 제공한다.
 */
interface BluetoothConnectionManager {

    /**
     * 현재 블루투스 연결 상태를 발행하는 StateFlow.
     * 상태: Disconnected → Connecting → Connected → Reconnecting
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Arduino로부터 수신된 파싱된 메시지를 발행하는 SharedFlow.
     * 연결이 활성 상태일 때만 메시지가 발행된다.
     */
    val incomingMessages: SharedFlow<BluetoothMessage>

    /**
     * 지정된 BluetoothDevice에 SPP 연결을 시도한다.
     *
     * @param device 연결할 BluetoothDevice (HC-06)
     * @return 성공 시 Result.success(Unit), 실패 시 Result.failure(exception)
     */
    suspend fun connect(device: BluetoothDevice): Result<Unit>

    /**
     * 현재 연결을 명시적으로 해제한다.
     * 이 경우 자동 재연결을 수행하지 않는다.
     */
    suspend fun disconnect()

    /**
     * 연결된 소켓을 통해 명령 문자열을 전송한다.
     *
     * @param command 전송할 명령 문자열
     * @return 성공 시 Result.success(Unit), 실패 시 Result.failure(exception)
     */
    suspend fun send(command: String): Result<Unit>

    /**
     * 연결 상태 폴링 모니터링을 시작한다.
     * 5초 간격으로 연결 상태를 확인하고, 끊어진 경우 자동 재연결을 시도한다.
     */
    fun startMonitoring()

    /**
     * 연결 상태 폴링 모니터링을 중지한다.
     */
    fun stopMonitoring()
}
