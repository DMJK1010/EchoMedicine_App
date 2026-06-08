package com.echomedicine.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.echomedicine.app.data.repository.ScheduleRepositoryImpl
import com.echomedicine.app.service.BluetoothForegroundService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 기기 재부팅 완료 시 호출되는 BroadcastReceiver.
 *
 * - 저장된 스케줄을 기반으로 복용 알람을 다시 등록한다 (AlarmManager는 재부팅 시 초기화됨).
 * - 백그라운드 블루투스 수신 Foreground Service를 다시 시작한다.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    @Inject
    lateinit var scheduleRepository: ScheduleRepositoryImpl

    @Inject
    lateinit var alarmScheduler: MedicineAlarmScheduler

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        Log.d(TAG, "Boot completed - restoring alarms and service")

        // 저장된 스케줄로 알람 재등록 (goAsync로 비동기 작업 보장)
        val pendingResult = goAsync()
        scope.launch {
            try {
                val schedules = scheduleRepository.getSchedules().first()
                alarmScheduler.scheduleAll(schedules)
                Log.d(TAG, "Re-scheduled ${schedules.size} alarms after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore alarms: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }

        // 백그라운드 수신 서비스 재시작
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BluetoothForegroundService::class.java)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart service after boot: ${e.message}")
        }
    }
}
