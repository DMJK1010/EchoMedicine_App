package com.echomedicine.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomedicine.app.domain.model.HistoryRecord
import com.echomedicine.app.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

/**
 * 복용 이력 화면의 ViewModel.
 *
 * 최근 90일간의 날짜별 이력을 조회하고,
 * 사용자가 날짜를 선택하면 해당 날짜의 상세 기록을 표시한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    /**
     * 최근 90일간의 전체 복용 이력.
     */
    val historyList: StateFlow<List<HistoryRecord>> = historyRepository
        .getHistorySince(ninetyDaysAgoMillis())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * 현재 선택된 날짜 (밀리초, 자정 기준).
     */
    private val _selectedDate = MutableStateFlow(todayMillis())
    val selectedDate: StateFlow<Long> = _selectedDate.asStateFlow()

    /**
     * 선택된 날짜의 상세 복용 기록.
     */
    val selectedDateHistory: StateFlow<List<HistoryRecord>> = _selectedDate
        .flatMapLatest { date ->
            historyRepository.getHistoryByDate(date)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * 최근 7일간 복용률 (0.0 ~ 100.0).
     */
    val takenRate: StateFlow<Double> = historyRepository
        .getTakenRate(sevenDaysAgoMillis())
        .map { rate -> if (rate.isNaN()) 0.0 else rate }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0.0
        )

    /**
     * 선택된 날짜에 기록이 비어있는지 여부.
     */
    val isEmpty: StateFlow<Boolean> = selectedDateHistory
        .map { it.isEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    /**
     * 날짜를 선택하여 상세 기록을 조회한다.
     *
     * @param dateMillis 선택한 날짜의 밀리초 값 (자정 기준으로 정규화됨)
     */
    fun selectDate(dateMillis: Long) {
        _selectedDate.value = normalizeToMidnight(dateMillis)
    }

    private fun normalizeToMidnight(millis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun todayMillis(): Long {
        return normalizeToMidnight(System.currentTimeMillis())
    }

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

    private fun ninetyDaysAgoMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -90)
        }
        return calendar.timeInMillis
    }
}
