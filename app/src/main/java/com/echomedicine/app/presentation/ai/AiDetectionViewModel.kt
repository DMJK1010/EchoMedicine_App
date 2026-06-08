package com.echomedicine.app.presentation.ai

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomedicine.app.domain.repository.HistoryRepository
import com.echomedicine.app.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiDetectionViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val historyRepository: HistoryRepository
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
                    historyRepository.recordTaken(
                        slotNumber = targetSchedule.slotNumber,
                        medicineName = targetSchedule.medicineName,
                        scheduledHour = targetSchedule.hour,
                        scheduledMinute = targetSchedule.minute
                    )
                    _isDetected.value = true
                }
            } catch (e: Exception) {
                // Error handling can be added here
            } finally {
                _isLoading.value = false
            }
        }
    }
}