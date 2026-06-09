package com.echomedicine.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.echomedicine.app.R
import com.echomedicine.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * 메인 Activity.
 *
 * - Bottom Navigation Bar와 Navigation Component 연결
 * - Hilt DI 주입 (@AndroidEntryPoint)
 * - 다크 모드/라이트 모드 자동 전환 (시스템 설정 연동)
 * - 복약 알림을 위한 알림 권한(POST_NOTIFICATIONS, API 33+) 요청
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 결과와 무관하게 진행 (거부 시 설정에서 직접 허용 안내) */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 시스템 설정에 따라 다크 모드/라이트 모드 자동 전환
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        requestNotificationPermissionIfNeeded()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Bottom Navigation과 NavController 연결
        binding.bottomNavigation.setupWithNavController(navController)
    }

    /**
     * Android 13(API 33) 이상에서 복약 알림 표시를 위해 POST_NOTIFICATIONS 권한을 요청한다.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
