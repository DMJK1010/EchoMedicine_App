package com.echomedicine.app.data.sync

import android.util.Log
import com.echomedicine.app.data.bluetooth.BluetoothConnectionManager
import com.echomedicine.app.data.bluetooth.BluetoothMessageSerializer
import com.echomedicine.app.data.preference.AppPreferences
import com.echomedicine.app.data.repository.HistoryRepositoryImpl
import com.echomedicine.app.data.repository.ScheduleRepositoryImpl
import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.domain.model.MedicineSlot
import com.echomedicine.app.domain.model.Schedule
import com.echomedicine.app.domain.model.SlotStatus
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 실시간 메시지 처리 및 데이터 동기화를 담당하는 매니저.
 *
 * 주요 역할:
 * - 복용 완료(TakenConfirmation) 수신 → HistoryRepository에 TAKEN 기록 저장 + Slot 상태 갱신
 * - 자정 초기화(DailyReset) 수신 → 미복용 Slot을 MISSED로 기록 + Slot 상태 갱신
 * - 연결 수립/재연결 시 → 자동 GET 명령 → 스케줄 캐시 갱신
 * - DataStore로 마지막 동기화 시각 관리
 * - 현재 Slot 상태를 StateFlow로 발행하여 UI 레이어에서 관찰 가능
 *
 * Requirements: 5.7, 6.1, 6.2, 6.7, 8.1, 8.2, 8.3, 8.5, 8.6
 */
@Singleton
class MessageSyncManager @Inject constructor(
    private val connectionManager: BluetoothConnectionManager,
    private val historyRepository: HistoryRepositoryImpl,
    private val scheduleRepository: ScheduleRepositoryImpl,
    private val appPreferences: AppPreferences,
    private val coroutineScope: CoroutineScope
) {

    companion object {
        private const val TAG = "MessageSyncManager"

        /** GET 응답 수신 대기 타임아웃 (밀리초) */
        private const val GET_RESPONSE_TIMEOUT_MS = 3_000L

        /** 기대하는 스케줄 슬롯 수 */
        private const val EXPECTED_SLOT_COUNT = 3
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception: ${throwable.message}", throwable)
    }

    private var messageCollectionJob: Job? = null
    private var connectionObserverJob: Job? = null

    /** 동기화 매니저 활성화 여부 */
    @Volatile
    private var isStarted = false

    /**
     * 3개 Slot의 현재 상태를 발행하는 StateFlow.
     * Schedule 데이터와 오늘의 History 기록을 조합하여 실시간 갱신된다.
     */
    private val _slotStates = MutableStateFlow<List<MedicineSlot>>(
        (0 until EXPECTED_SLOT_COUNT).map { slot ->
            MedicineSlot(slot, "", 0, 0, SlotStatus.EMPTY)
        }
    )
    val slotStates: StateFlow<List<MedicineSlot>> = _slotStates.asStateFlow()

    /**
     * 동기화 이벤트를 발행하는 SharedFlow.
     * 동기화 성공/실패 등의 이벤트를 UI에 전달한다.
     */
    private val _syncEvents = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 16)
    val syncEvents: SharedFlow<SyncEvent> = _syncEvents.asSharedFlow()

    /**
     * 동기화 매니저를 시작한다.
     *
     * - 수신 메시지를 수집하여 데이터를 영속화한다.
     * - 연결 상태를 관찰하여 연결/재연결 시 자동 동기화를 수행한다.
     * - 시작 시 Slot 상태를 즉시 갱신한다.
     *
     * BluetoothForegroundService 또는 Application에서 호출한다.
     */
    fun start() {
        if (isStarted) return
        isStarted = true
        Log.d(TAG, "MessageSyncManager started")

        observeIncomingMessages()
        observeConnectionState()

        // 시작 시 캐시된 데이터로 Slot 상태를 즉시 갱신
        coroutineScope.launch(exceptionHandler) {
            refreshSlotStates()
        }
    }

    /**
     * 동기화 매니저를 중지한다.
     */
    fun stop() {
        isStarted = false
        messageCollectionJob?.cancel()
        messageCollectionJob = null
        connectionObserverJob?.cancel()
        connectionObserverJob = null
        Log.d(TAG, "MessageSyncManager stopped")
    }

    /**
     * 외부(예: AI 인식 화면, 대시보드 진입)에서 Slot 상태 즉시 갱신을 요청한다.
     *
     * DB(스케줄/이력)가 다른 경로로 변경된 경우, 이 메서드를 호출하면
     * 최신 데이터로 slotStates를 다시 계산하여 UI에 반영한다.
     * start() 여부와 무관하게 동작한다(앱 레벨 coroutineScope 사용).
     */
    fun requestRefresh() {
        coroutineScope.launch(exceptionHandler) {
            refreshSlotStates()
        }
    }

    /**
     * 수신 메시지를 수집하고 데이터 영속화를 수행한다.
     *
     * - TakenConfirmation → HistoryRepository에 TAKEN 기록 저장 + Slot 상태 갱신
     * - DailyReset → 미복용 Slot을 MISSED로 기록 + Slot 상태 갱신
     * - ScheduleInfo → 스케줄 캐시 갱신 (GET 응답 처리 시 별도 경로로도 처리)
     */
    private fun observeIncomingMessages() {
        messageCollectionJob?.cancel()
        messageCollectionJob = coroutineScope.launch(exceptionHandler) {
            connectionManager.incomingMessages.collect { message ->
                handleMessageForPersistence(message)
            }
        }
    }

    /**
     * 연결 상태를 관찰하여, Connected 상태 진입 시 자동 GET 명령을 전송하여
     * 스케줄 캐시를 갱신한다.
     */
    private fun observeConnectionState() {
        connectionObserverJob?.cancel()
        connectionObserverJob = coroutineScope.launch(exceptionHandler) {
            connectionManager.connectionState
                .filter { it is ConnectionState.Connected }
                .collect {
                    Log.d(TAG, "Connection established, triggering auto-sync")
                    performAutoSync()
                }
        }
    }

    /**
     * 수신 메시지를 처리하여 데이터를 영속화한다.
     */
    private suspend fun handleMessageForPersistence(message: BluetoothMessage) {
        when (message) {
            is BluetoothMessage.TakenConfirmation -> {
                handleTakenConfirmation(message)
            }

            is BluetoothMessage.DailyReset -> {
                handleDailyReset()
            }

            else -> {
                // 다른 메시지 타입은 이 레이어에서 처리하지 않음
            }
        }
    }

    /**
     * 복용 완료 메시지를 처리한다.
     *
     * 약 이름으로 캐시된 스케줄에서 해당 Slot을 찾아
     * HistoryRepository에 TAKEN 기록을 저장하고 Slot 상태를 갱신한다.
     *
     * Requirements: 6.1
     */
    private suspend fun handleTakenConfirmation(message: BluetoothMessage.TakenConfirmation) {
        Log.d(TAG, "Processing TakenConfirmation: ${message.medicineName}")

        // 캐시된 스케줄에서 약 이름이 일치하는 Slot을 찾는다
        val schedule = findScheduleByMedicineName(message.medicineName)

        if (schedule != null) {
            // 이미 오늘 기록이 있는지 확인 (중복 방지)
            val today = todayMillis()
            val existingRecord = historyRepository.getRecord(today, schedule.slotNumber)
            if (existingRecord == null) {
                historyRepository.recordTaken(
                    slotNumber = schedule.slotNumber,
                    medicineName = schedule.medicineName,
                    scheduledHour = schedule.hour,
                    scheduledMinute = schedule.minute
                )
                Log.d(TAG, "Recorded TAKEN for slot ${schedule.slotNumber}: ${schedule.medicineName}")
            } else {
                Log.d(TAG, "Record already exists for slot ${schedule.slotNumber} today, skipping")
            }
        } else {
            Log.w(TAG, "No cached schedule found for medicine: ${message.medicineName}")
            // 스케줄을 찾을 수 없는 경우에도 약 이름과 기본값으로 기록
            historyRepository.recordTaken(
                slotNumber = 0,
                medicineName = message.medicineName,
                scheduledHour = 0,
                scheduledMinute = 0
            )
        }

        // Slot 상태 갱신
        refreshSlotStates()
    }

    /**
     * 자정 초기화 메시지를 처리한다.
     *
     * 현재 캐시된 모든 활성 스케줄에서 오늘 TAKEN 기록이 없는 Slot을
     * MISSED로 기록한다.
     *
     * Requirements: 6.2, 6.7
     */
    private suspend fun handleDailyReset() {
        Log.d(TAG, "Processing DailyReset - recording missed slots")

        val today = todayMillis()

        // 모든 캐시된 스케줄을 순회
        for (slot in 0 until EXPECTED_SLOT_COUNT) {
            val schedule = scheduleRepository.getBySlot(slot)
            if (schedule != null && schedule.medicineName.isNotBlank()) {
                // 오늘 해당 Slot에 대한 기록이 없으면 MISSED 처리
                val existingRecord = historyRepository.getRecord(today, slot)
                if (existingRecord == null) {
                    historyRepository.recordMissed(
                        slotNumber = slot,
                        medicineName = schedule.medicineName,
                        scheduledHour = schedule.hour,
                        scheduledMinute = schedule.minute
                    )
                    Log.d(TAG, "Recorded MISSED for slot $slot: ${schedule.medicineName}")
                }
            }
        }

        // Slot 상태 갱신
        refreshSlotStates()
    }

    /**
     * 연결 수립 시 자동으로 GET 명령을 전송하여 스케줄 캐시를 갱신한다.
     *
     * Requirements: 5.7, 8.5, 8.6
     */
    private suspend fun performAutoSync() {
        Log.d(TAG, "Performing auto-sync: sending GET command")

        val getCommand = BluetoothMessageSerializer.serializeGetCommand()
        val sendResult = connectionManager.send(getCommand)

        if (sendResult.isFailure) {
            Log.e(TAG, "Failed to send GET command: ${sendResult.exceptionOrNull()?.message}")
            _syncEvents.tryEmit(SyncEvent.SyncFailed("GET 명령 전송 실패"))
            return
        }

        // GET 응답으로 ScheduleInfo 메시지 3개를 수집 (타임아웃 3초)
        val schedules = collectScheduleResponses()

        if (schedules.size == EXPECTED_SLOT_COUNT) {
            // 3개 모두 수신 성공 → 캐시 갱신
            scheduleRepository.cacheSchedules(schedules)
            val syncTime = System.currentTimeMillis()
            appPreferences.updateLastSyncTime(syncTime)
            Log.d(TAG, "Auto-sync successful: ${schedules.size} schedules cached at $syncTime")
            _syncEvents.tryEmit(SyncEvent.SyncSuccess(syncTime))

            // 스케줄 갱신 후 Slot 상태도 갱신
            refreshSlotStates()
        } else {
            Log.w(TAG, "Auto-sync incomplete: received ${schedules.size}/$EXPECTED_SLOT_COUNT schedules")
            _syncEvents.tryEmit(SyncEvent.SyncFailed("GET 응답 타임아웃 (${schedules.size}/$EXPECTED_SLOT_COUNT)"))
            // Requirements 8.6: 동기화 실패 시 기존 캐시 유지, 로그만 기록
        }
    }

    /**
     * GET 응답으로 전송되는 ScheduleInfo 메시지를 타임아웃 내에 수집한다.
     *
     * @return 수집된 Schedule 목록 (최대 3개)
     */
    private suspend fun collectScheduleResponses(): List<Schedule> {
        val collected = mutableListOf<Schedule>()

        withTimeoutOrNull(GET_RESPONSE_TIMEOUT_MS) {
            connectionManager.incomingMessages
                .filter { it is BluetoothMessage.ScheduleInfo }
                .take(EXPECTED_SLOT_COUNT)
                .collect { message ->
                    val info = message as BluetoothMessage.ScheduleInfo
                    collected.add(
                        Schedule(
                            slotNumber = info.slot,
                            medicineName = info.name,
                            hour = info.hour,
                            minute = info.minute
                        )
                    )
                }
        }

        return collected
    }

    /**
     * 캐시된 스케줄과 오늘의 복용 기록을 조합하여 Slot 상태를 갱신한다.
     *
     * - 스케줄이 없는 Slot → EMPTY
     * - 오늘 TAKEN 기록이 있는 Slot → TAKEN
     * - 오늘 MISSED 기록이 있는 Slot → MISSED
     * - 기록이 없는 활성 Slot → WAITING
     */
    private suspend fun refreshSlotStates() {
        val today = todayMillis()
        val slots = mutableListOf<MedicineSlot>()

        for (slotNumber in 0 until EXPECTED_SLOT_COUNT) {
            val schedule = scheduleRepository.getBySlot(slotNumber)

            if (schedule == null || schedule.medicineName.isBlank()) {
                slots.add(MedicineSlot(slotNumber, "", 0, 0, SlotStatus.EMPTY))
            } else {
                val record = historyRepository.getRecord(today, slotNumber)
                val status = when {
                    record != null && record.status == SlotStatus.TAKEN -> SlotStatus.TAKEN
                    record != null && record.status == SlotStatus.MISSED -> SlotStatus.MISSED
                    else -> SlotStatus.WAITING
                }
                slots.add(
                    MedicineSlot(
                        slotNumber = schedule.slotNumber,
                        medicineName = schedule.medicineName,
                        hour = schedule.hour,
                        minute = schedule.minute,
                        status = status
                    )
                )
            }
        }

        _slotStates.value = slots
        Log.d(TAG, "Slot states refreshed: ${slots.map { "${it.slotNumber}:${it.status}" }}")
    }

    /**
     * 캐시된 스케줄에서 약 이름으로 스케줄을 찾는다.
     */
    private suspend fun findScheduleByMedicineName(medicineName: String): Schedule? {
        for (slot in 0 until EXPECTED_SLOT_COUNT) {
            val schedule = scheduleRepository.getBySlot(slot)
            if (schedule != null && schedule.medicineName == medicineName) {
                return schedule
            }
        }
        return null
    }

    /**
     * 오늘 날짜의 0시 0분 0초 0밀리초에 해당하는 밀리초 값을 반환한다.
     */
    private fun todayMillis(): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}

/**
 * 동기화 이벤트 sealed class.
 * UI 레이어에서 동기화 결과를 표시하는 데 사용된다.
 */
sealed class SyncEvent {
    data class SyncSuccess(val syncTimeMillis: Long) : SyncEvent()
    data class SyncFailed(val reason: String) : SyncEvent()
}
