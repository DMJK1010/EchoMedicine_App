package com.echomedicine.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.echomedicine.app.data.bluetooth.BluetoothConnectionManager
import com.echomedicine.app.data.sync.MessageSyncManager
import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 앱이 백그라운드 상태일 때도 블루투스 수신을 유지하는 Foreground Service.
 *
 * 주요 역할:
 * - Foreground Notification(bt_service 채널)을 표시하여 서비스 유지
 * - BluetoothConnectionManager를 통해 수신되는 메시지를 수집
 * - 메시지 타입에 따라 적절한 Notification 발행 (복용 알림, 복용 완료, 미복용 경고)
 * - 연결 상태 모니터링 및 재연결 로직을 ConnectionManager에 위임
 *
 * Requirements: 2.5, 5.6, 5.7
 */
@AndroidEntryPoint
class BluetoothForegroundService : Service() {

    companion object {
        private const val TAG = "BtForegroundService"

        /**
         * 서비스를 시작하기 위한 Intent를 생성한다.
         */
        fun newStartIntent(context: Context): Intent {
            return Intent(context, BluetoothForegroundService::class.java)
        }
    }

    @Inject
    lateinit var connectionManager: BluetoothConnectionManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var messageSyncManager: MessageSyncManager

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception: ${throwable.message}", throwable)
    }

    private val serviceScope = CoroutineScope(
        Dispatchers.Main + SupervisorJob() + exceptionHandler
    )

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            notificationHelper.buildServiceNotification()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        // 연결 상태 모니터링 시작 (재연결 로직 포함)
        connectionManager.startMonitoring()

        // 실시간 메시지 처리 및 데이터 동기화 시작
        messageSyncManager.start()

        // 수신 메시지를 수집하고 타입별 Notification 발행
        collectIncomingMessages()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        messageSyncManager.stop()
        connectionManager.stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * BluetoothConnectionManager의 incomingMessages SharedFlow를 수집하여
     * 메시지 타입에 따라 적절한 Notification을 발행한다.
     */
    private fun collectIncomingMessages() {
        serviceScope.launch {
            connectionManager.incomingMessages.collect { message ->
                handleMessage(message)
            }
        }
    }

    /**
     * 수신된 BluetoothMessage의 타입에 따라 적절한 Notification을 표시한다.
     *
     * - MedicineAlert → 복용 알림 Notification (HIGH 중요도)
     * - TakenConfirmation → 복용 완료 Notification (DEFAULT 중요도)
     * - RealertWarning → 미복용 경고 Notification (HIGH 중요도)
     * - 그 외 메시지는 Notification을 발행하지 않음
     */
    private fun handleMessage(message: BluetoothMessage) {
        when (message) {
            is BluetoothMessage.MedicineAlert -> {
                Log.d(TAG, "Medicine alert received: ${message.medicineName}")
                val notification = notificationHelper.buildMedicineAlertNotification(
                    message.medicineName
                )
                notificationManager.notify(
                    NotificationHelper.NOTIFICATION_ID_ALERT,
                    notification
                )
            }

            is BluetoothMessage.TakenConfirmation -> {
                Log.d(TAG, "Taken confirmation received: ${message.medicineName}")
                val notification = notificationHelper.buildTakenNotification(
                    message.medicineName
                )
                notificationManager.notify(
                    NotificationHelper.NOTIFICATION_ID_TAKEN,
                    notification
                )
            }

            is BluetoothMessage.RealertWarning -> {
                Log.d(TAG, "Realert warning received: ${message.medicineName}")
                val notification = notificationHelper.buildWarningNotification(
                    message.medicineName
                )
                notificationManager.notify(
                    NotificationHelper.NOTIFICATION_ID_WARNING,
                    notification
                )
            }

            is BluetoothMessage.SettingConfirmation -> {
                Log.d(TAG, "Setting confirmation received: ${message.medicineName}")
                // 설정 완료 메시지는 별도 Notification을 발행하지 않음
                // ViewModel에서 처리
            }

            is BluetoothMessage.DailyReset -> {
                Log.d(TAG, "Daily reset received")
                // 자정 초기화 메시지 — Notification 없이 로그만 기록
                // 데이터 처리는 별도 레이어에서 담당
            }

            is BluetoothMessage.ScheduleInfo -> {
                Log.d(TAG, "Schedule info received: slot=${message.slot}")
                // 스케줄 정보는 Notification 없이 처리
            }

            is BluetoothMessage.Unknown -> {
                Log.w(TAG, "Unknown message received: ${message.raw}")
                // 인식 불가 메시지 — 무시
            }
        }
    }
}
