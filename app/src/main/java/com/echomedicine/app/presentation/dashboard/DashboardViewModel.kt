package com.echomedicine.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomedicine.app.data.sync.MessageSyncManager
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.domain.model.MedicineSlot
import com.echomedicine.app.domain.repository.BluetoothRepository
import com.echomedicine.app.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

/**
 * 대시보드 화면의 ViewModel.
 *
 * 3개 Slot 상태, 블루투스 연결 상태, 최근 7일 복용률을 관찰한다.
 * Slot 상태는 MessageSyncManager가 관리하는 실시간 slotStates를 관찰한다.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val messageSyncManager: MessageSyncManager,
    historyRepository: HistoryRepository,
    bluetoothRepository: BluetoothRepository
) : ViewModel() {

    /**
     * 3개 Slot의 현재 상태.
     * MessageSyncManager가 스케줄 데이터와 오늘의 복용 기록을 조합하여
     * 실시간으로 갱신하는 slotStates를 관찰한다.
     *
     * 상태: EMPTY (미배정), WAITING (대기 중), TAKEN (복용 완료), MISSED (미복용)
     */
    val slots: StateFlow<List<MedicineSlot>> = messageSyncManager.slotStates

    /**
     * 블루투스 연결 상태.
     */
    val connectionState: StateFlow<ConnectionState> = bluetoothRepository.connectionState

    /**
     * 최근 7일간 복용률 (0.0 ~ 100.0).
     */
    val takenRate: StateFlow<Double> = historyRepository.getTakenRate(sevenDaysAgoMillis())
        .map { rate -> if (rate.isNaN()) 0.0 else rate }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0.0
        )

    private fun sevenDaysAgoMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -7)
        }
        return calendar.timeInMillis
    }

    /**
     * 화면이 다시 표시될 때(예: AI 인식 후 복귀) 최신 복용 상태로 Slot을 갱신한다.
     */
    fun refreshSlots() {
        messageSyncManager.requestRefresh()
    }
}
