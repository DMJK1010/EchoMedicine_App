package com.echomedicine.app.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Android 버전별 런타임 권한 관리를 담당하는 유틸리티 객체.
 *
 * - API 31 미만: ACCESS_FINE_LOCATION (블루투스 스캔에 필요)
 * - API 31 이상: BLUETOOTH_CONNECT, BLUETOOTH_SCAN
 * - API 33 이상: POST_NOTIFICATIONS (Foreground Service 알림에 필요)
 *
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.7
 */
object PermissionHelper {

    /**
     * 블루투스 관련 필수 권한 목록을 반환한다.
     *
     * API 31 미만: ACCESS_FINE_LOCATION
     * API 31 이상: BLUETOOTH_CONNECT, BLUETOOTH_SCAN
     */
    fun getRequiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * 알림 권한 목록을 반환한다.
     * API 33 이상에서만 POST_NOTIFICATIONS 권한이 필요하며,
     * 그 미만에서는 빈 배열을 반환한다.
     */
    fun getNotificationPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }

    /**
     * 모든 블루투스 관련 권한이 부여되었는지 확인한다.
     *
     * @param context 앱 컨텍스트
     * @return 모든 블루투스 권한이 허용되었으면 true
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        return getRequiredBluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 알림 권한이 부여되었는지 확인한다.
     * API 33 미만에서는 항상 true를 반환한다 (권한 요청 불필요).
     *
     * @param context 앱 컨텍스트
     * @return 알림 권한이 허용되었으면 true
     */
    fun hasNotificationPermission(context: Context): Boolean {
        val permissions = getNotificationPermissions()
        if (permissions.isEmpty()) return true
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 주어진 권한 목록 중 하나라도 Rationale을 표시해야 하는지 확인한다.
     * 사용자가 한 번 이상 거부한 경우 true를 반환한다.
     *
     * @param activity 현재 Activity
     * @param permissions 확인할 권한 배열
     * @return Rationale을 표시해야 하면 true
     */
    fun shouldShowRationale(activity: Activity, permissions: Array<String>): Boolean {
        return permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    /**
     * 앱 설정 화면으로 이동하는 Intent를 생성한다.
     * 사용자가 권한을 영구 거부한 경우, 이 Intent로 시스템 앱 설정에서
     * 직접 권한을 활성화하도록 안내한다.
     *
     * @param context 앱 컨텍스트
     * @return 앱 상세 설정 화면을 여는 Intent
     */
    fun getAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 권한 요청 결과를 분석하여 영구 거부 여부를 판단한다.
     *
     * 영구 거부 조건:
     * - 권한이 부여되지 않았고 (PERMISSION_DENIED)
     * - shouldShowRequestPermissionRationale이 false인 경우
     *   (사용자가 "다시 묻지 않음"을 선택한 상태)
     *
     * @param activity 현재 Activity
     * @param permission 확인할 권한
     * @return 영구 거부 상태이면 true
     */
    fun isPermanentlyDenied(activity: Activity, permission: String): Boolean {
        val isNotGranted = ContextCompat.checkSelfPermission(activity, permission) !=
            PackageManager.PERMISSION_GRANTED
        val shouldNotShowRationale =
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        return isNotGranted && shouldNotShowRationale
    }
}
