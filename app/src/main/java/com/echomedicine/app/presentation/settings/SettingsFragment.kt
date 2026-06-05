package com.echomedicine.app.presentation.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.echomedicine.app.BuildConfig
import com.echomedicine.app.R
import com.echomedicine.app.databinding.FragmentSettingsBinding
import com.echomedicine.app.service.BluetoothForegroundService
import dagger.hilt.android.AndroidEntryPoint

/**
 * 설정 화면 Fragment.
 *
 * - 다크 모드 자동 전환 정보 표시 (시스템 설정 연동)
 * - Foreground Service 토글
 * - 앱 버전 정보 표시
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

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
    }

    private fun setupVersionInfo() {
        binding.tvVersionValue.text = getString(R.string.settings_version_value, BuildConfig.VERSION_NAME)
    }

    private fun setupForegroundServiceToggle() {
        // Default to enabled
        binding.switchForegroundService.isChecked = true

        binding.switchForegroundService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startForegroundService()
            } else {
                stopForegroundService()
            }
        }
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
