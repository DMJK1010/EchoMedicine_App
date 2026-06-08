package com.echomedicine.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.echomedicine.app.domain.model.Schedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 복용 시간에 맞춰 안드로이드 AlarmManager로 자체 알람을 등록/취소하는 스케줄러.
 *
 * 블루투스 연결 여부와 무관하게, 폰이 스스로 지정된 시각에 알람을 울린다.
 * 매일 같은 시각에 반복되도록 설정하며, 부팅 후에는 BootReceiver가 재등록한다.
 */
@Singleton
class MedicineAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "MedicineAlarmScheduler"

        const val ACTION_MEDICINE_ALARM = "com.echomedicine.app.ACTION_MEDICINE_ALARM"
        const val EXTRA_SLOT_NUMBER = "extra_slot_number"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"

        /** PendingIntent requestCode 베이스 (slotNumber를 더해 고유화) */
        private const val REQUEST_CODE_BASE = 1000
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 단일 스케줄에 대한 매일 반복 알람을 등록한다.
     * 약 이름이 비어있으면 등록하지 않는다.
     */
    fun schedule(schedule: Schedule) {
        if (schedule.medicineName.isBlank()) {
            cancel(schedule.slotNumber)
            return
        }

        val triggerAt = nextTriggerMillis(schedule.hour, schedule.minute)
        val pendingIntent = buildPendingIntent(schedule)

        try {
            // 정확한 시각 알람. 도즈 모드에서도 울리도록 setExactAndAllowWhileIdle 사용.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                // 정확한 알람 권한이 없으면 부정확 알람으로 폴백 (그래도 매일 울림)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                Log.w(TAG, "Exact alarm not permitted; scheduled inexact for slot ${schedule.slotNumber}")
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm for slot ${schedule.slotNumber} at ${schedule.hour}:${schedule.minute}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule alarm: ${e.message}")
        }
    }

    /**
     * 여러 스케줄을 한 번에 등록한다.
     */
    fun scheduleAll(schedules: List<Schedule>) {
        schedules.forEach { schedule(it) }
    }

    /**
     * 지정된 Slot의 알람을 취소한다.
     */
    fun cancel(slotNumber: Int) {
        val intent = Intent(context, MedicineAlarmReceiver::class.java).apply {
            action = ACTION_MEDICINE_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + slotNumber,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for slot $slotNumber")
        }
    }

    /**
     * 알람이 울린 후, 다음 날 같은 시각으로 재등록한다.
     * (setExact는 1회성이므로 Receiver에서 다시 호출해야 매일 반복된다.)
     */
    fun rescheduleForNextDay(schedule: Schedule) {
        schedule(schedule)
    }

    private fun buildPendingIntent(schedule: Schedule): PendingIntent {
        val intent = Intent(context, MedicineAlarmReceiver::class.java).apply {
            action = ACTION_MEDICINE_ALARM
            putExtra(EXTRA_SLOT_NUMBER, schedule.slotNumber)
            putExtra(EXTRA_MEDICINE_NAME, schedule.medicineName)
            putExtra(EXTRA_HOUR, schedule.hour)
            putExtra(EXTRA_MINUTE, schedule.minute)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + schedule.slotNumber,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 오늘 지정 시각이 이미 지났으면 내일, 아니면 오늘의 해당 시각 밀리초를 반환한다.
     */
    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }
}
