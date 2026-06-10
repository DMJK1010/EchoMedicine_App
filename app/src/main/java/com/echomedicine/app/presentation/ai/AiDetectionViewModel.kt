package com.echomedicine.app.presentation.ai

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomedicine.app.data.sync.MessageSyncManager
import com.echomedicine.app.domain.repository.BluetoothRepository
import com.echomedicine.app.domain.repository.HistoryRepository
import com.echomedicine.app.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AiDetectionViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val historyRepository: HistoryRepository,
    private val messageSyncManager: MessageSyncManager,
    private val bluetoothRepository: BluetoothRepository
) : ViewModel() {

    private val _isDetected = MutableStateFlow(false)
    val isDetected: StateFlow<Boolean> = _isDetected.asStateFlow()

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun markAsTaken(slotNumber: Int) {
        if (_isDetected.value) return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Get the schedule to record history
                val schedules = scheduleRepository.getSchedules().first()
                val targetSchedule = schedules.find { it.slotNumber == slotNumber }
                
                if (targetSchedule != null) {
                    // 오늘 이미 기록이 있으면 중복 저장하지 않음
                    val today = todayMillis()
                    val existing = historyRepository.getRecord(today, targetSchedule.slotNumber)
                    if (existing == null) {
                        historyRepository.recordTaken(
                            slotNumber = targetSchedule.slotNumber,
                            medicineName = targetSchedule.medicineName,
                            scheduledHour = targetSchedule.hour,
                            scheduledMinute = targetSchedule.minute
                        )
                    }
                    _isDetected.value = true
                    // 보관함에 복용 완료를 알려 해당 칸을 즉시 닫는다
                    bluetoothRepository.send("DONE:$slotNumber\n")
                    // 홈 화면(대시보드)의 복용 상태가 즉시 갱신되도록 요청
                    messageSyncManager.requestRefresh()
                }
            } catch (e: Exception) {
                // Error handling can be added here
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun todayMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}