package com.echomedicine.app.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject

/**
 * BluetoothConnectionManager의 구현체.
 *
 * SPP UUID를 사용하여 Bluetooth Classic 소켓 연결을 관리하며,
 * 연결 상태 모니터링, 자동 재연결, 메시지 수신/파싱을 처리한다.
 *
 * @param coroutineScope 코루틴 실행에 사용되는 스코프 (Hilt에서 주입)
 * @param messageParser 수신 메시지 파서
 */
class BluetoothConnectionManagerImpl @Inject constructor(
    private val coroutineScope: CoroutineScope,
    private val messageParser: BluetoothMessageParser
) : BluetoothConnectionManager {

    companion object {
        /** SPP (Serial Port Profile) UUID */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** 연결 타임아웃 (밀리초) */
        private const val CONNECTION_TIMEOUT_MS = 5_000L

        /** 연결 상태 폴링 간격 (밀리초) */
        private const val POLLING_INTERVAL_MS = 5_000L

        /** 재연결 간격 (밀리초) */
        private const val RECONNECT_INTERVAL_MS = 3_000L

        /** 최대 재연결 시도 횟수 */
        private const val MAX_RECONNECT_ATTEMPTS = 5

        /** InputStream 읽기 버퍼 크기 */
        private const val BUFFER_SIZE = 1024
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<BluetoothMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<BluetoothMessage> = _incomingMessages.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    /** 마지막 연결된 기기 (재연결에 사용) */
    private var lastConnectedDevice: BluetoothDevice? = null

    /** 사용자가 명시적으로 연결 해제를 요청했는지 여부 */
    private var isExplicitDisconnect = false

    /** 수신 루프 Job */
    private var readJob: Job? = null

    /** 모니터링 Job */
    private var monitoringJob: Job? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: BluetoothDevice): Result<Unit> {
        if (_connectionState.value is ConnectionState.Connected) {
            return Result.success(Unit)
        }

        _connectionState.value = ConnectionState.Connecting
        isExplicitDisconnect = false

        return try {
            val btSocket = withTimeout(CONNECTION_TIMEOUT_MS) {
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()
                s
            }

            socket = btSocket
            inputStream = btSocket.inputStream
            outputStream = btSocket.outputStream
            lastConnectedDevice = device

            _connectionState.value = ConnectionState.Connected
            startReadLoop()

            Result.success(Unit)
        } catch (e: Exception) {
            closeSocketSilently()
            _connectionState.value = ConnectionState.Disconnected
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        isExplicitDisconnect = true
        stopMonitoring()
        stopReadLoop()
        closeSocketSilently()
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun send(command: String): Result<Unit> {
        val os = outputStream ?: return Result.failure(
            IOException("BluetoothSocket is not connected")
        )

        return try {
            os.write(command.toByteArray(Charsets.UTF_8))
            os.flush()
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = coroutineScope.launch {
            while (isActive) {
                delay(POLLING_INTERVAL_MS)
                checkConnectionAndReconnect()
            }
        }
    }

    override fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * 연결 상태를 확인하고, 예기치 않은 끊김이면 재연결을 시도한다.
     */
    private suspend fun checkConnectionAndReconnect() {
        if (isExplicitDisconnect) return
        if (_connectionState.value is ConnectionState.Reconnecting) return

        val currentSocket = socket
        val isConnected = try {
            currentSocket != null && currentSocket.isConnected
        } catch (_: Exception) {
            false
        }

        if (!isConnected && _connectionState.value is ConnectionState.Connected) {
            // 예기치 않은 연결 끊김 감지 → 재연결 시도
            attemptReconnection()
        }
    }

    /**
     * 자동 재연결을 최대 MAX_RECONNECT_ATTEMPTS 회 시도한다.
     */
    @SuppressLint("MissingPermission")
    private suspend fun attemptReconnection() {
        val device = lastConnectedDevice ?: run {
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        stopReadLoop()
        closeSocketSilently()

        for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
            if (isExplicitDisconnect) {
                _connectionState.value = ConnectionState.Disconnected
                return
            }

            _connectionState.value = ConnectionState.Reconnecting(
                attempt = attempt,
                maxAttempts = MAX_RECONNECT_ATTEMPTS
            )

            try {
                val btSocket = withTimeout(CONNECTION_TIMEOUT_MS) {
                    val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    s.connect()
                    s
                }

                socket = btSocket
                inputStream = btSocket.inputStream
                outputStream = btSocket.outputStream

                _connectionState.value = ConnectionState.Connected
                startReadLoop()
                return
            } catch (_: Exception) {
                closeSocketSilently()
                if (attempt < MAX_RECONNECT_ATTEMPTS) {
                    delay(RECONNECT_INTERVAL_MS)
                }
            }
        }

        // 모든 재연결 시도 실패
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * InputStream에서 지속적으로 데이터를 읽어 파싱 후 SharedFlow로 발행하는 루프를 시작한다.
     */
    private fun startReadLoop() {
        readJob?.cancel()
        readJob = coroutineScope.launch {
            val buffer = ByteArray(BUFFER_SIZE)
            val messageBuffer = StringBuilder()

            try {
                while (isActive) {
                    val stream = inputStream ?: break
                    val bytesRead = stream.read(buffer)
                    if (bytesRead == -1) {
                        // 스트림 종료 → 연결 끊김
                        break
                    }

                    val received = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    messageBuffer.append(received)

                    // 개행문자 기준으로 완전한 메시지를 분리하여 처리
                    val content = messageBuffer.toString()
                    val lastNewline = content.lastIndexOf('\n')
                    if (lastNewline >= 0) {
                        val completeMessages = content.substring(0, lastNewline + 1)
                        val remainder = content.substring(lastNewline + 1)
                        messageBuffer.clear()
                        messageBuffer.append(remainder)

                        val messages = messageParser.splitMessages(completeMessages)
                        for (msg in messages) {
                            val parsed = messageParser.parse(msg)
                            _incomingMessages.emit(parsed)
                        }
                    }
                }
            } catch (_: IOException) {
                // 소켓 닫힘 또는 읽기 오류
            }

            // 읽기 루프 종료 → 연결이 끊어진 것으로 처리
            if (!isExplicitDisconnect && _connectionState.value is ConnectionState.Connected) {
                attemptReconnection()
            }
        }
    }

    /**
     * 읽기 루프를 중지한다.
     */
    private fun stopReadLoop() {
        readJob?.cancel()
        readJob = null
    }

    /**
     * 소켓과 스트림을 조용히 닫는다 (예외 무시).
     */
    private fun closeSocketSilently() {
        try {
            inputStream?.close()
        } catch (_: IOException) {}
        try {
            outputStream?.close()
        } catch (_: IOException) {}
        try {
            socket?.close()
        } catch (_: IOException) {}
        inputStream = null
        outputStream = null
        socket = null
    }
}
