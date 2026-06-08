package com.echomedicine.app.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.echomedicine.app.domain.model.Schedule
import com.echomedicine.app.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 복용 시간 알람이 울릴 때 호출되는 BroadcastReceiver.
 *
 * - 복용 알림 Notification을 발행한다 (블루투스 연결과 무관).
 * - 1회성 알람이므로 다음 날 같은 시각으로 재등록한다.
 */
@AndroidEntryPoint
class MedicineAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MedicineAlarmReceiver"
    }

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var alarmScheduler: MedicineAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MedicineAlarmScheduler.ACTION_MEDICINE_ALARM) return

        val slotNumber = intent.getIntExtra(MedicineAlarmScheduler.EXTRA_SLOT_NUMBER, -1)
        val medicineName = intent.getStringExtra(MedicineAlarmScheduler.EXTRA_MEDICINE_NAME) ?: ""
        val hour = intent.getIntExtra(MedicineAlarmScheduler.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(MedicineAlarmScheduler.EXTRA_MINUTE, -1)

        if (slotNumber < 0 || medicineName.isBlank()) {
            Log.w(TAG, "Invalid alarm payload: slot=$slotNumber, name=$medicineName")
            return
        }

        Log.d(TAG, "Medicine alarm fired: slot=$slotNumber, name=$medicineName")

        // 복용 알림 발행
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NotificationHelper.NOTIFICATION_ID_ALERT + slotNumber,
            notificationHelper.buildMedicineAlertNotification(medicineName)
        )

        // 다음 날 같은 시각으로 재등록 (매일 반복)
        if (hour in 0..23 && minute in 0..59) {
            alarmScheduler.rescheduleForNextDay(
                Schedule(
                    slotNumber = slotNumber,
                    medicineName = medicineName,
                    hour = hour,
                    minute = minute
                )
            )
        }
    }
}
