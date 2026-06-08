package com.echomedicine.app.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomedicine.app.data.bluetooth.BluetoothMessageSerializer
import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.domain.model.Schedule
import com.echomedicine.app.domain.repository.BluetoothRepository
import com.echomedicine.app.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * 스케줄 설정 화면의 ViewModel.
 *
 * SET 명령 전송 및 설정 확인 응답 대기, GET 명령 전송 및 스케줄 조회 결과 표시를 담당한다.
 * 입력 유효성 검증(약 이름 빈 값/15바이트 초과, 시간 범위), 연결 상태 확인을 수행한다.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 4.1, 4.2, 4.3, 4.4
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val scheduleRepository: ScheduleRepository,
    private val alarmScheduler: com.echomedicine.app.alarm.MedicineAlarmScheduler
) : ViewModel() {

    companion object {
        private const val SET_TIMEOUT_MS = 3000L
        private const val GET_TIMEOUT_MS = 3000L
        private const val TOTAL_SLOTS = 3
        private const val MEDICINE_NAME_MAX_BYTES = 15
    }

    /** 블루투스 연결 상태 */
    val connectionState: StateFlow<ConnectionState> = bluetoothRepository.connectionState

    /** 캐싱된 스케줄 목록 (Room DB에서 관찰) */
    val schedules: StateFlow<List<Schedule>> = scheduleRepository.getSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiEvent = MutableSharedFlow<ScheduleUiEvent>()
    /** UI 이벤트 (토스트, 에러 메시지 등) */
    val uiEvent: SharedFlow<ScheduleUiEvent> = _uiEvent.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    /** 로딩 상태 */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 스케줄을 저장한다 (SET 명령 전송).
     *
     * 1. 입력 유효성 검증
     * 2. 블루투스 연결 상태 확인
     * 3. SET 명령 직렬화 및 전송
     * 4. 3초 이내 SettingConfirmation 응답 대기
     *
     * Requirement 3.2: SET 명령 전송
     * Requirement 3.3: 설정 확인 응답 수신 시 성공 토스트
     * Requirement 3.4: 약 이름 15바이트 초과 시 오류
     * Requirement 3.5: 연결 미수립 시 안내 메시지
     * Requirement 3.6: 시간 범위 초과 시 오류
     * Requirement 3.7: 약 이름 빈 값 시 오류
     * Requirement 3.8: 3초 타임아웃 시 오류 메시지
     *
     * @param slotNumber 칸 번호 (0~2)
     * @param medicineName 약 이름
     * @param hour 시 (0~23)
     * @param minute 분 (0~59)
     */
    fun saveSchedule(slotNumber: Int, medicineName: String, hour: Int, minute: Int) {
        viewModelScope.launch {
            // 입력 유효성 검증
            val validationError = validateInput(slotNumber, medicineName, hour, minute)
            if (validationError != null) {
                _uiEvent.emit(validationError)
                return@launch
            }

            // 블루투스 연결 상태 확인
            if (connectionState.value !is ConnectionState.Connected) {
                _uiEvent.emit(ScheduleUiEvent.NotConnectedError)
                return@launch
            }

            // SET 명령 직렬화
            val schedule = Schedule(slotNumber, medicineName.trim(), hour, minute)
            val serializeResult = BluetoothMessageSerializer.serializeSetCommand(schedule)
            if (serializeResult.isFailure) {
                _uiEvent.emit(
                    ScheduleUiEvent.ValidationError(
                        slotNumber,
                        serializeResult.exceptionOrNull()?.message ?: "직렬화 오류"
                    )
                )
                return@launch
            }

            _isLoading.value = true

            // SET 명령 전송
            val sendResult = bluetoothRepository.send(serializeResult.getOrThrow())
            if (sendResult.isFailure) {
                _isLoading.value = false
                _uiEvent.emit(ScheduleUiEvent.SendError(sendResult.exceptionOrNull()?.message ?: "전송 실패"))
                return@launch
            }

            // 3초 이내 SettingConfirmation 응답 대기
            val confirmation = withTimeoutOrNull(SET_TIMEOUT_MS) {
                bluetoothRepository.incomingMessages.first { message ->
                    message is BluetoothMessage.SettingConfirmation
                }
            }

            _isLoading.value = false

            if (confirmation != null) {
                // 설정 성공 → 로컬 DB에도 캐싱하여 홈 화면/목록에 즉시 반영
                scheduleRepository.cacheSchedule(schedule)
                // 자체 시간 알람 등록 (블루투스 연결과 무관하게 폰이 직접 알림)
                alarmScheduler.schedule(schedule)
                _uiEvent.emit(ScheduleUiEvent.SetSuccess(slotNumber))
            } else {
                _uiEvent.emit(ScheduleUiEvent.TimeoutError)
            }
        }
    }

    /**
     * 스케줄을 조회한다 (GET 명령 전송).
     *
     * 1. 블루투스 연결 상태 확인
     * 2. GET 명령 전송
     * 3. 3초 이내 3개 ScheduleInfo 메시지 수신 대기
     * 4. 수신 성공 시 캐싱 및 화면 갱신
     *
     * Requirement 4.1: GET 명령 전송
     * Requirement 4.2: 3개 Slot 스케줄 표시
     * Requirement 4.3: 3초 이내 미수신 시 조회 실패
     * Requirement 4.4: 연결 해제 상태에서 조회 시 오류 메시지
     */
    fun refreshSchedules() {
        viewModelScope.launch {
            // 블루투스 연결 상태 확인
            if (connectionState.value !is ConnectionState.Connected) {
                _uiEvent.emit(ScheduleUiEvent.NotConnectedError)
                return@launch
            }

            _isLoading.value = true

            // GET 명령 전송
            val command = BluetoothMessageSerializer.serializeGetCommand()
            val sendResult = bluetoothRepository.send(command)
            if (sendResult.isFailure) {
                _isLoading.value = false
                _uiEvent.emit(ScheduleUiEvent.SendError(sendResult.exceptionOrNull()?.message ?: "전송 실패"))
                return@launch
            }

            // 3초 이내 3개 ScheduleInfo 메시지 수집
            val scheduleInfos = withTimeoutOrNull(GET_TIMEOUT_MS) {
                bluetoothRepository.incomingMessages
                    .filter { it is BluetoothMessage.ScheduleInfo }
                    .take(TOTAL_SLOTS)
                    .toList()
                    .filterIsInstance<BluetoothMessage.ScheduleInfo>()
            }

            _isLoading.value = false

            if (scheduleInfos != null && scheduleInfos.size >= TOTAL_SLOTS) {
                // 성공: 수신된 스케줄을 캐싱
                val scheduleList = scheduleInfos.map { info ->
                    Schedule(
                        slotNumber = info.slot,
                        medicineName = info.name,
                        hour = info.hour,
                        minute = info.minute
                    )
                }
                scheduleRepository.cacheSchedules(scheduleList)
                // 조회된 스케줄로 자체 알람도 갱신
                alarmScheduler.scheduleAll(scheduleList)
                _uiEvent.emit(ScheduleUiEvent.GetSuccess)
            } else {
                // 타임아웃: 부분 데이터 폐기
                _uiEvent.emit(ScheduleUiEvent.GetTimeoutError)
            }
        }
    }

    /**
     * 입력값 유효성 검증.
     *
     * @return 유효성 검증 오류 이벤트 (유효하면 null)
     */
    private fun validateInput(
        slotNumber: Int,
        medicineName: String,
        hour: Int,
        minute: Int
    ): ScheduleUiEvent? {
        // 약 이름 빈 값/공백만 구성 검증
        if (medicineName.isBlank()) {
            return ScheduleUiEvent.ValidationError(
                slotNumber,
                "약 이름을 입력해주세요."
            )
        }

        // 약 이름 15바이트 초과 검증
        val nameBytes = medicineName.trim().toByteArray(Charsets.UTF_8)
        if (nameBytes.size > MEDICINE_NAME_MAX_BYTES) {
            return ScheduleUiEvent.ValidationError(
                slotNumber,
                "약 이름은 15바이트(한글 5자) 이내로 입력해주세요."
            )
        }

        // 시간 범위 검증
        if (hour !in 0..23) {
            return ScheduleUiEvent.ValidationError(
                slotNumber,
                "시간은 0~23 범위로 입력해주세요."
            )
        }

        if (minute !in 0..59) {
            return ScheduleUiEvent.ValidationError(
                slotNumber,
                "분은 0~59 범위로 입력해주세요."
            )
        }

        return null
    }
}

/**
 * 스케줄 화면 UI 이벤트.
 */
sealed class ScheduleUiEvent {
    /** SET 명령 성공 */
    data class SetSuccess(val slotNumber: Int) : ScheduleUiEvent()
    /** GET 명령 성공 */
    object GetSuccess : ScheduleUiEvent()
    /** 유효성 검증 오류 */
    data class ValidationError(val slotNumber: Int, val message: String) : ScheduleUiEvent()
    /** 블루투스 미연결 오류 */
    object NotConnectedError : ScheduleUiEvent()
    /** SET 응답 타임아웃 오류 */
    object TimeoutError : ScheduleUiEvent()
    /** GET 응답 타임아웃 오류 */
    object GetTimeoutError : ScheduleUiEvent()
    /** 전송 오류 */
    data class SendError(val message: String) : ScheduleUiEvent()
}
