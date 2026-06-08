package com.echomedicine.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomedicine.app.data.preference.AppPreferences
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.domain.repository.BluetoothRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 설정 화면의 ViewModel.
 *
 * - 마지막 동기화 시각 관찰
 * - 블루투스 연결 상태 관찰
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    appPreferences: AppPreferences,
    bluetoothRepository: BluetoothRepository
) : ViewModel() {

    /** 마지막 동기화 시각 (밀리초, null이면 동기화 이력 없음) */
    val lastSyncTime: StateFlow<Long?> = appPreferences.lastSyncTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 블루투스 연결 상태 */
    val connectionState: StateFlow<ConnectionState> = bluetoothRepository.connectionState
}
