package com.echomedicine.app.presentation.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.echomedicine.app.BuildConfig
import com.echomedicine.app.R
import com.echomedicine.app.databinding.FragmentSettingsBinding
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.service.BluetoothForegroundService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 설정 화면 Fragment.
 *
 * - 연결 상태/마지막 동기화 시각 표시 및 연결 화면 이동
 * - 알림 권한 상태 표시 및 시스템 알림 설정 이동
 * - 다크 모드 선택 (시스템/라이트/다크)
 * - Foreground Service 토글
 * - 앱 버전/정보/도움말/오픈소스 라이선스
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    private val syncTimeFormat = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupVersionInfo()
        setupForegroundServiceToggle()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // 시스템 알림 설정에서 돌아왔을 때 상태 갱신
        updateNotificationStatus()
        // 서비스 실행 상태를 토글에 반영 (리스너 트리거 없이 갱신)
        val running = BluetoothForegroundService.isRunning
        if (binding.switchForegroundService.isChecked != running) {
            binding.switchForegroundService.setOnCheckedChangeListener(null)
            binding.switchForegroundService.isChecked = running
            binding.switchForegroundService.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) startForegroundService() else stopForegroundService()
            }
        }
    }

    private fun setupVersionInfo() {
        binding.tvVersionValue.text =
            getString(R.string.settings_version_value, BuildConfig.VERSION_NAME)
    }

    private fun setupForegroundServiceToggle() {
        // 실제 서비스 실행 상태를 토글에 반영
        binding.switchForegroundService.isChecked = BluetoothForegroundService.isRunning
        binding.switchForegroundService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startForegroundService() else stopForegroundService()
        }
    }

    private fun setupClickListeners() {
        // 연결 화면 이동
        binding.btnOpenConnection.setOnClickListener { navigateToConnection() }
        binding.cardConnection.setOnClickListener { navigateToConnection() }

        // 알림 설정 이동
        binding.btnOpenNotification.setOnClickListener { openNotificationSettings() }

        // 오픈소스 라이선스
        binding.btnLicenses.setOnClickListener { showLicensesDialog() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.connectionState.collect { updateConnectionStatus(it) }
                }
                launch {
                    viewModel.lastSyncTime.collect { updateLastSyncTime(it) }
                }
            }
        }
    }

    private fun updateConnectionStatus(state: ConnectionState) {
        binding.tvConnectionStatusValue.text = when (state) {
            is ConnectionState.Connected -> getString(R.string.settings_connection_connected)
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting -> getString(R.string.settings_connection_connecting)
            is ConnectionState.Disconnected -> getString(R.string.settings_connection_disconnected)
        }
    }

    private fun updateLastSyncTime(timeMillis: Long?) {
        binding.tvLastSyncValue.text = if (timeMillis == null || timeMillis <= 0L) {
            getString(R.string.settings_last_sync_never)
        } else {
            getString(
                R.string.settings_last_sync_format,
                syncTimeFormat.format(Date(timeMillis))
            )
        }
    }

    private fun showLicensesDialog() {
        val licenses = """
            • AndroidX (Apache 2.0)
            • Material Components for Android (Apache 2.0)
            • Hilt / Dagger (Apache 2.0)
            • Room (Apache 2.0)
            • Kotlin Coroutines (Apache 2.0)
            • Navigation Component (Apache 2.0)
            • DataStore (Apache 2.0)
            • CameraX (Apache 2.0)
            • ML Kit Pose Detection (Google APIs)
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_licenses_title)
            .setMessage(licenses)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun updateNotificationStatus() {
        val enabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        binding.tvNotificationStatus.text = getString(
            if (enabled) R.string.settings_notification_enabled
            else R.string.settings_notification_disabled
        )
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", requireContext().packageName, null)
            }
        }
        startActivity(intent)
    }

    private fun navigateToConnection() {
        findNavController().navigate(R.id.navigation_connection)
    }

    private fun startForegroundService() {
        val intent = Intent(requireContext(), BluetoothForegroundService::class.java)
        requireContext().startForegroundService(intent)
    }

    private fun stopForegroundService() {
        val intent = Intent(requireContext(), BluetoothForegroundService::class.java)
        requireContext().stopService(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
