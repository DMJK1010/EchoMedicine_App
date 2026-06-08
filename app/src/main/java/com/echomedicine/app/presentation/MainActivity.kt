package com.echomedicine.app.presentation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.echomedicine.app.R
import com.echomedicine.app.data.preference.AppPreferences
import com.echomedicine.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 메인 Activity.
 *
 * - Bottom Navigation Bar와 Navigation Component 연결
 * - Hilt DI 주입 (@AndroidEntryPoint)
 * - 저장된 다크 모드 설정 적용 (사용자 선택 또는 시스템 설정)
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 저장된 다크 모드 설정을 적용한다 (기본: 시스템 설정 따름)
        lifecycleScope.launch {
            val mode = appPreferences.darkMode.first()
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    AppPreferences.DARK_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    AppPreferences.DARK_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Bottom Navigation과 NavController 연결
        binding.bottomNavigation.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
