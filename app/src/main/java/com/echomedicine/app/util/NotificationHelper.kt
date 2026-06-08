package com.echomedicine.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationHelper는 Echo Medicine 앱의 모든 Notification 채널 생성 및
 * 메시지 타입별 Notification 빌드를 담당한다.
 *
 * 채널:
 * - bt_service (LOW): Foreground Service 상태 표시
 * - medicine_alert (HIGH): 복용 알림 (💊)
 * - medicine_taken (DEFAULT): 복용 완료 (✅)
 * - medicine_warning (HIGH): 미복용 경고 (⚠️)
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_BT_SERVICE = "bt_service"
        const val CHANNEL_MEDICINE_ALERT = "medicine_alert"
        const val CHANNEL_MEDICINE_TAKEN = "medicine_taken"
        const val CHANNEL_MEDICINE_WARNING = "medicine_warning"

        const val NOTIFICATION_ID_SERVICE = 1
        const val NOTIFICATION_ID_ALERT = 100
        const val NOTIFICATION_ID_TAKEN = 200
        const val NOTIFICATION_ID_WARNING = 300
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * 앱 시작 시 호출하여 4개 Notification 채널을 등록한다.
     */
    fun createNotificationChannels() {
        val channels = listOf(
            NotificationChannel(
                CHANNEL_BT_SERVICE,
                "블루투스 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "블루투스 연결 유지를 위한 Foreground Service 상태 표시"
            },
            NotificationChannel(
                CHANNEL_MEDICINE_ALERT,
                "복용 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "약 복용 시간 알림"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_MEDICINE_TAKEN,
                "복용 완료",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "약 복용 완료 확인 알림"
            },
            NotificationChannel(
                CHANNEL_MEDICINE_WARNING,
                "미복용 경고",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "약 미복용 재알림 경고"
                enableVibration(true)
            }
        )

        notificationManager.createNotificationChannels(channels)
    }

    /**
     * Foreground Service용 Notification을 빌드한다.
     * 낮은 중요도로 상태바에 지속 표시된다.
     */
    fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_BT_SERVICE)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Echo Medicine")
            .setContentText("블루투스 연결 유지 중")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * 💊 복용 알림 Notification을 빌드한다.
     * HIGH 중요도로 사운드와 진동을 포함한다.
     *
     * @param medicineName 알림 대상 약 이름
     */
    fun buildMedicineAlertNotification(medicineName: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_MEDICINE_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💊 복용 시간입니다")
            .setContentText("$medicineName 복용 시간이에요!")
            .setContentIntent(buildOpenAppIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
    }

    /**
     * 앱을 여는 PendingIntent를 생성한다 (알림 탭 시 MainActivity 실행).
     */
    private fun buildOpenAppIntent(): android.app.PendingIntent? {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: return null
        launchIntent.flags =
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        return android.app.PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * ✅ 복용 완료 Notification을 빌드한다.
     * DEFAULT 중요도로 표시된다.
     *
     * @param medicineName 복용 완료된 약 이름
     */
    fun buildTakenNotification(medicineName: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_MEDICINE_TAKEN)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✅ 복용 완료")
            .setContentText("$medicineName 복용이 확인되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    /**
     * ⚠️ 미복용 경고 Notification을 빌드한다.
     * HIGH 중요도로 사운드와 진동을 포함한다.
     *
     * @param medicineName 미복용 경고 대상 약 이름
     */
    fun buildWarningNotification(medicineName: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_MEDICINE_WARNING)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 미복용 경고")
            .setContentText("$medicineName 아직 복용하지 않았어요!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }
}
