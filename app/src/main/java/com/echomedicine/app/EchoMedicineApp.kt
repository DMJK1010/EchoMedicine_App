package com.echomedicine.app

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.echomedicine.app.service.BluetoothForegroundService
import com.echomedicine.app.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EchoMedicineApp : Application() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannels()
        startBluetoothService()
    }

    /**
     * 앱 시작 시 백그라운드 블루투스 수신 Foreground Service를 자동으로 시작한다.
     * 사용자가 설정에서 끄지 않는 한 항상 동작하도록 한다.
     */
    private fun startBluetoothService() {
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, BluetoothForegroundService::class.java)
            )
        } catch (e: Exception) {
            Log.e("EchoMedicineApp", "Failed to auto-start service: ${e.message}")
        }
    }
}
