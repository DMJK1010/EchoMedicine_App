package com.echomedicine.app.presentation.connection

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.domain.repository.BluetoothRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 블루투스 연결 화면의 ViewModel.
 *
 * 페어링된 기기 목록 조회, 연결/해제, 연결 상태 관찰을 담당한다.
 *
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) : ViewModel() {

    /** 블루투스 연결 상태를 관찰하는 StateFlow */
    val connectionState: StateFlow<ConnectionState> = bluetoothRepository.connectionState

    private val _pairedDevices = MutableLiveData<List<BluetoothDevice>>(emptyList())
    /** 페어링된 블루투스 기기 목록 */
    val pairedDevices: LiveData<List<BluetoothDevice>> = _pairedDevices

    private val _connectionError = MutableLiveData<String?>()
    /** 연결 오류 메시지 (null이면 오류 없음) */
    val connectionError: LiveData<String?> = _connectionError

    /**
     * 페어링된 블루투스 기기 목록을 로드한다.
     * Requirement 1.1: 2초 이내에 페어링된 기기 목록을 표시한다.
     */
    fun loadPairedDevices() {
        val devices = bluetoothRepository.getPairedDevices()
        _pairedDevices.value = devices
    }

    /**
     * 지정된 블루투스 기기에 연결을 시도한다.
     * Requirement 1.3: SPP UUID를 사용하여 연결을 시도하고 진행 중 상태를 표시한다.
     * Requirement 1.5: 연결 타임아웃 시 오류 메시지를 표시한다.
     *
     * @param device 연결할 BluetoothDevice
     */
    fun connectToDevice(device: BluetoothDevice) {
        _connectionError.value = null
        viewModelScope.launch {
            val result = bluetoothRepository.connect(device)
            result.onFailure { throwable ->
                _connectionError.value = throwable.message ?: "연결에 실패했습니다."
            }
        }
    }

    /**
     * 현재 블루투스 연결을 해제한다.
     * Requirement 2.7: 사용자가 명시적으로 연결 해제를 요청하면 재연결을 시도하지 않는다.
     */
    fun disconnect() {
        viewModelScope.launch {
            bluetoothRepository.disconnect()
        }
    }

    /** 연결 오류 메시지를 소비(클리어)한다. */
    fun clearError() {
        _connectionError.value = null
    }
}
