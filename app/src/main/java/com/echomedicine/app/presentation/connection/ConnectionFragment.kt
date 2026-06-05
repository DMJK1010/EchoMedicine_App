package com.echomedicine.app.presentation.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.echomedicine.app.R
import com.echomedicine.app.databinding.FragmentConnectionBinding
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.util.PermissionHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 블루투스 연결 화면 Fragment.
 *
 * 페어링된 기기 목록을 표시하고, 기기 선택 시 연결을 시도한다.
 * 블루투스 비활성화 시 활성화 요청 다이얼로그를 표시하며,
 * 페어링된 기기가 없을 때는 시스템 블루투스 설정 이동을 안내한다.
 *
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7
 */
@AndroidEntryPoint
class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConnectionViewModel by viewModels()

    private val bluetoothAdapter: BluetoothAdapter?
        get() {
            val manager = requireContext().getSystemService(
                android.content.Context.BLUETOOTH_SERVICE
            ) as? android.bluetooth.BluetoothManager
            return manager?.adapter
        }

    private lateinit var pairedDeviceAdapter: PairedDeviceAdapter

    /**
     * 블루투스 활성화 요청 결과 처리.
     * Requirement 1.6: 사용자가 거부할 경우 안내 메시지 표시 및 연결 기능 비활성화.
     */
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (bluetoothAdapter?.isEnabled == true) {
            loadDevices()
        } else {
            showBluetoothRequiredMessage()
        }
    }

    /**
     * 블루투스 권한 요청 결과 처리.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkBluetoothAndLoad()
        } else {
            showPermissionDeniedMessage()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        checkPermissionsAndLoad()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        pairedDeviceAdapter = PairedDeviceAdapter { device ->
            viewModel.connectToDevice(device)
        }
        binding.rvPairedDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pairedDeviceAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }

        binding.btnReconnect.setOnClickListener {
            checkPermissionsAndLoad()
        }

        binding.btnOpenBtSettings.setOnClickListener {
            openBluetoothSettings()
        }
    }

    private fun observeViewModel() {
        // 연결 상태 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.connectionState.collect { state ->
                    updateConnectionStatusUI(state)
                }
            }
        }

        // 페어링 기기 목록 관찰
        viewModel.pairedDevices.observe(viewLifecycleOwner) { devices ->
            updateDeviceListUI(devices)
        }

        // 연결 오류 관찰
        viewModel.connectionError.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    /**
     * 연결 상태에 따른 UI 업데이트.
     */
    private fun updateConnectionStatusUI(state: ConnectionState) {
        when (state) {
            is ConnectionState.Disconnected -> {
                binding.tvConnectionStatus.text = getString(R.string.status_disconnected)
                binding.btnDisconnect.visibility = View.GONE
                binding.btnReconnect.visibility = View.VISIBLE
                binding.rvPairedDevices.visibility = View.VISIBLE
            }
            is ConnectionState.Connecting -> {
                binding.tvConnectionStatus.text = getString(R.string.status_connecting)
                binding.btnDisconnect.visibility = View.GONE
                binding.btnReconnect.visibility = View.GONE
            }
            is ConnectionState.Connected -> {
                binding.tvConnectionStatus.text = getString(R.string.status_connected)
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.btnReconnect.visibility = View.GONE
            }
            is ConnectionState.Reconnecting -> {
                binding.tvConnectionStatus.text = getString(
                    R.string.status_reconnecting,
                    state.attempt,
                    state.maxAttempts
                )
                binding.btnDisconnect.visibility = View.GONE
                binding.btnReconnect.visibility = View.GONE
            }
        }
    }

    /**
     * 기기 목록 UI 업데이트.
     * Requirement 1.2: 페어링된 기기가 없으면 안내 메시지와 설정 이동 옵션 제공.
     */
    @SuppressLint("MissingPermission")
    private fun updateDeviceListUI(devices: List<android.bluetooth.BluetoothDevice>) {
        if (devices.isEmpty()) {
            binding.rvPairedDevices.visibility = View.GONE
            binding.layoutNoDevices.visibility = View.VISIBLE
        } else {
            binding.rvPairedDevices.visibility = View.VISIBLE
            binding.layoutNoDevices.visibility = View.GONE
            pairedDeviceAdapter.submitList(devices)
        }
    }

    /**
     * 권한 확인 후 기기 목록 로드.
     */
    private fun checkPermissionsAndLoad() {
        if (!PermissionHelper.hasBluetoothPermissions(requireContext())) {
            permissionLauncher.launch(PermissionHelper.getRequiredBluetoothPermissions())
        } else {
            checkBluetoothAndLoad()
        }
    }

    /**
     * 블루투스 활성화 확인 후 기기 목록 로드.
     * Requirement 1.6: 블루투스 비활성화 시 활성화 요청 다이얼로그 표시.
     */
    private fun checkBluetoothAndLoad() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            showBluetoothRequiredMessage()
            return
        }
        if (!adapter.isEnabled) {
            showEnableBluetoothDialog()
        } else {
            loadDevices()
        }
    }

    private fun loadDevices() {
        viewModel.loadPairedDevices()
    }

    /**
     * 블루투스 활성화 요청 다이얼로그 표시.
     * Requirement 1.6
     */
    private fun showEnableBluetoothDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bluetooth_disabled_title)
            .setMessage(R.string.bluetooth_disabled_message)
            .setPositiveButton(R.string.enable_bluetooth) { _, _ ->
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                showBluetoothRequiredMessage()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 블루투스가 필요하다는 안내 메시지 표시 및 연결 기능 비활성화.
     */
    private fun showBluetoothRequiredMessage() {
        Snackbar.make(
            binding.root,
            R.string.bluetooth_required_message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    /**
     * 권한 거부 시 안내 메시지 표시.
     */
    private fun showPermissionDeniedMessage() {
        Snackbar.make(
            binding.root,
            R.string.permission_denied_message,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.settings) {
            startActivity(PermissionHelper.getAppSettingsIntent(requireContext()))
        }.show()
    }

    /**
     * 시스템 블루투스 설정 화면을 연다.
     * Requirement 1.2: 페어링된 기기가 없을 때 시스템 블루투스 설정 이동 안내.
     */
    private fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        startActivity(intent)
    }
}
