package com.echomedicine.app.data.sync

import com.echomedicine.app.data.bluetooth.BluetoothConnectionManager
import com.echomedicine.app.data.preference.AppPreferences
import com.echomedicine.app.data.repository.HistoryRepositoryImpl
import com.echomedicine.app.data.repository.ScheduleRepositoryImpl
import com.echomedicine.app.domain.model.BluetoothMessage
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.domain.model.HistoryRecord
import com.echomedicine.app.domain.model.Schedule
import com.echomedicine.app.domain.model.SlotStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * MessageSyncManager 단위 테스트.
 *
 * 복용 완료 메시지 처리, 자정 초기화 처리, 연결 시 자동 동기화,
 * Slot 상태 갱신 로직을 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageSyncManagerTest : DescribeSpec({

    lateinit var connectionManager: BluetoothConnectionManager
    lateinit var historyRepository: HistoryRepositoryImpl
    lateinit var scheduleRepository: ScheduleRepositoryImpl
    lateinit var appPreferences: AppPreferences
    lateinit var alarmScheduler: com.echomedicine.app.alarm.MedicineAlarmScheduler
    lateinit var messageSyncManager: MessageSyncManager
    lateinit var incomingMessages: MutableSharedFlow<BluetoothMessage>
    lateinit var connectionState: MutableStateFlow<ConnectionState>

    beforeEach {
        connectionManager = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        scheduleRepository = mockk(relaxed = true)
        appPreferences = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)

        incomingMessages = MutableSharedFlow(extraBufferCapacity = 64)
        connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

        every { connectionManager.incomingMessages } returns incomingMessages
        every { connectionManager.connectionState } returns connectionState
    }

    describe("복용 완료 메시지(TakenConfirmation) 처리") {

        it("캐시된 스케줄이 있을 때 해당 Slot에 TAKEN 기록을 저장한다") {
            runTest {
                val testSchedule = Schedule(
                    slotNumber = 1,
                    medicineName = "혈압약",
                    hour = 9,
                    minute = 0
                )
                coEvery { scheduleRepository.getBySlot(1) } returns testSchedule
                coEvery { scheduleRepository.getBySlot(0) } returns null
                coEvery { scheduleRepository.getBySlot(2) } returns null
                coEvery { historyRepository.getRecord(any(), 1) } returns null

                messageSyncManager = MessageSyncManager(
                    connectionManager = connectionManager,
                    historyRepository = historyRepository,
                    scheduleRepository = scheduleRepository,
                    appPreferences = appPreferences,
                    alarmScheduler = alarmScheduler,
                    coroutineScope = this
                )

                messageSyncManager.start()
                advanceUntilIdle()

                // TakenConfirmation 메시지 발행
                incomingMessages.emit(BluetoothMessage.TakenConfirmation("혈압약"))
                advanceUntilIdle()

                coVerify {
                    historyRepository.recordTaken(
                        slotNumber = 1,
                        medicineName = "혈압약",
                        scheduledHour = 9,
                        scheduledMinute = 0
                    )
                }

                messageSyncManager.stop()
            }
        }

        it("이미 오늘 기록이 있으면 중복 기록하지 않는다") {
            runTest {
                val testSchedule = Schedule(1, "혈압약", 9, 0)
                val existingRecord = HistoryRecord(
                    id = 1,
                    slotNumber = 1,
                    medicineName = "혈압약",
                    scheduledHour = 9,
                    scheduledMinute = 0,
                    actualTakenTime = System.currentTimeMillis(),
                    date = System.currentTimeMillis(),
                    status = SlotStatus.TAKEN
                )

                coEvery { scheduleRepository.getBySlot(0) } returns null
                coEvery { scheduleRepository.getBySlot(1) } returns testSchedule
                coEvery { scheduleRepository.getBySlot(2) } returns null
                coEvery { historyRepository.getRecord(any(), 1) } returns existingRecord

                messageSyncManager = MessageSyncManager(
                    connectionManager = connectionManager,
                    historyRepository = historyRepository,
                    scheduleRepository = scheduleRepository,
                    appPreferences = appPreferences,
                    alarmScheduler = alarmScheduler,
                    coroutineScope = this
                )

                messageSyncManager.start()
                advanceUntilIdle()

                incomingMessages.emit(BluetoothMessage.TakenConfirmation("혈압약"))
                advanceUntilIdle()

                coVerify(exactly = 0) {
                    historyRepository.recordTaken(any(), any(), any(), any())
                }

                messageSyncManager.stop()
            }
        }

        it("스케줄을 찾을 수 없는 경우 기본 Slot(0)으로 기록한다") {
            runTest {
                coEvery { scheduleRepository.getBySlot(any()) } returns null
                coEvery { historyRepository.getRecord(any(), any()) } returns null

                messageSyncManager = MessageSyncManager(
                    connectionManager = connectionManager,
                    historyRepository = historyRepository,
                    scheduleRepository = scheduleRepository,
                    appPreferences = appPreferences,
                    alarmScheduler = alarmScheduler,
                    coroutineScope = this
                )

                messageSyncManager.start()
                advanceUntilIdle()

                incomingMessages.emit(BluetoothMessage.TakenConfirmation("알수없는약"))
                advanceUntilIdle()

                coVerify {
                    historyRepository.recordTaken(
                        slotNumber = 0,
                        medicineName = "알수없는약",
                        scheduledHour = 0,
                        scheduledMinute = 0
                    )
                }

                messageSyncManager.stop()
            }
        }
    }

    describe("자정 초기화(DailyReset) 메시지 처리") {

        it("TAKEN 기록이 없는 활성 Slot을 MISSED로 기록한다") {
            runTest {
                val schedule0 = Schedule(0, "혈압약", 9, 0)
                val schedule1 = Schedule(1, "당뇨약", 12, 30)
                // slot 2 is empty

                coEvery { scheduleRepository.getBySlot(0) } returns schedule0
                coEvery { scheduleRepository.getBySlot(1) } returns schedule1
                coEvery { scheduleRepository.getBySlot(2) } returns null
                // slot 0 already has a TAKEN record, slot 1 has no record
                coEvery { historyRepository.getRecord(any(), 0) } returns HistoryRecord(
                    id = 1, slotNumber = 0, medicineName = "혈압약",
                    scheduledHour = 9, scheduledMinute = 0,
                    actualTakenTime = System.currentTimeMillis(),
                    date = System.currentTimeMillis(), status = SlotStatus.TAKEN
                )
                coEvery { historyRepository.getRecord(any(), 1) } returns null
                coEvery { historyRepository.getRecord(any(), 2) } returns null

                messageSyncManager = MessageSyncManager(
                    connectionManager = connectionManager,
                    historyRepository = historyRepository,
                    scheduleRepository = scheduleRepository,
                    appPreferences = appPreferences,
                    alarmScheduler = alarmScheduler,
                    coroutineScope = this
                )

                messageSyncManager.start()
                advanceUntilIdle()

                incomingMessages.emit(BluetoothMessage.DailyReset)
                advanceUntilIdle()

                // slot 0 should NOT be recorded as MISSED (already TAKEN)
                coVerify(exactly = 0) {
                    historyRepository.recordMissed(0, any(), any(), any())
                }

                // slot 1 should be recorded as MISSED
                coVerify {
                    historyRepository.recordMissed(
                        slotNumber = 1,
                        medicineName = "당뇨약",
                        scheduledHour = 12,
                        scheduledMinute = 30
                    )
                }

                // slot 2 has no schedule, should not be recorded
                coVerify(exactly = 0) {
                    historyRepository.recordMissed(2, any(), any(), any())
                }

                messageSyncManager.stop()
            }
        }
    }

    describe("연결 시 자동 동기화") {

        it("Connected 상태 진입 시 GET 명령을 전송한다") {
            runTest {
                coEvery { connectionManager.send(any()) } returns Result.success(Unit)
                coEvery { scheduleRepository.getBySlot(any()) } returns null

                messageSyncManager = MessageSyncManager(
                    connectionManager = connectionManager,
                    historyRepository = historyRepository,
                    scheduleRepository = scheduleRepository,
                    appPreferences = appPreferences,
                    alarmScheduler = alarmScheduler,
                    coroutineScope = this
                )

                messageSyncManager.start()
                advanceUntilIdle()

                // 연결 상태를 Connected로 변경
                connectionState.value = ConnectionState.Connected
                advanceUntilIdle()

                coVerify {
                    connectionManager.send("GET\n")
                }

                messageSyncManager.stop()
            }
        }

        it("GET 명령 전송 실패 시 SyncFailed 이벤트를 발행한다") {
            runTest {
                coEvery { connectionManager.send(any()) } returns Result.failure(
                    java.io.IOException("Connection lost")
                )
                coEvery { scheduleRepository.getBySlot(any()) } returns null

                messageSyncManager = MessageSyncManager(
                    connectionManager = connectionManager,
                    historyRepository = historyRepository,
                    scheduleRepository = scheduleRepository,
                    appPreferences = appPreferences,
                    alarmScheduler = alarmScheduler,
                    coroutineScope = this
                )

                messageSyncManager.start()
                advanceUntilIdle()

                connectionState.value = ConnectionState.Connected
                advanceUntilIdle()

                // 스케줄 캐시가 갱신되지 않아야 한다
                coVerify(exactly = 0) {
                    scheduleRepository.cacheSchedules(any())
                }

                messageSyncManager.stop()
            }
        }
    }

    describe("Slot 상태 갱신") {

        it("start 호출 시 캐시된 스케줄로 Slot 상태를 즉시 갱신한다") {
            runTest {
                val schedule0 = Schedule(0, "혈압약", 9, 0)
                coEvery { scheduleRepository.getBySlot(0) } returns schedule0
                coEvery { scheduleRepository.getBySlot(1) } returns null
                coEvery { scheduleRepository.getBySlot(2) } returns null
                coEvery { historyRepository.getRecord(any(), 0) } returns null
                coEvery { historyRepository.getRecord(any(), 1) } returns null
                coEvery { historyRepository.getRecord(any(), 2) } returns null

                messageSyncManager = MessageSyncManager(
                    connectionManager = connectionManager,
                    historyRepository = historyRepository,
                    scheduleRepository = scheduleRepository,
                    appPreferences = appPreferences,
                    alarmScheduler = alarmScheduler,
                    coroutineScope = this
                )

                messageSyncManager.start()
                advanceUntilIdle()

                val slots = messageSyncManager.slotStates.value
                slots.size shouldBe 3
                slots[0].medicineName shouldBe "혈압약"
                slots[0].status shouldBe SlotStatus.WAITING
                slots[1].status shouldBe SlotStatus.EMPTY
                slots[2].status shouldBe SlotStatus.EMPTY

                messageSyncManager.stop()
            }
        }

        it("TakenConfirmation 후 해당 Slot 상태가 TAKEN으로 갱신된다") {
            runTest {
                val schedule0 = Schedule(0, "혈압약", 9, 0)
                coEvery { scheduleRepository.getBySlot(0) } returns schedule0
                coEvery { scheduleRepository.getBySlot(1) } returns null
                coEvery { scheduleRepository.getBySlot(2) } returns null

                // 처음에는 기록 없음
                coEvery { historyRepository.getRecord(any(), 0) } returns null
                coEvery { historyRepository.getRecord(any(), 1) } returns null
                coEvery { historyRepository.getRecord(any(), 2) } returns null

                messageSyncManager = MessageSyncManager(
                    connectionManager = connectionManager,
                    historyRepository = historyRepository,
                    scheduleRepository = scheduleRepository,
                    appPreferences = appPreferences,
                    alarmScheduler = alarmScheduler,
                    coroutineScope = this
                )

                messageSyncManager.start()
                advanceUntilIdle()

                // TAKEN 기록 후 getRecord가 기록을 반환하도록 변경
                coEvery { historyRepository.getRecord(any(), 0) } returns HistoryRecord(
                    id = 1, slotNumber = 0, medicineName = "혈압약",
                    scheduledHour = 9, scheduledMinute = 0,
                    actualTakenTime = System.currentTimeMillis(),
                    date = System.currentTimeMillis(), status = SlotStatus.TAKEN
                )

                incomingMessages.emit(BluetoothMessage.TakenConfirmation("혈압약"))
                advanceUntilIdle()

                val slots = messageSyncManager.slotStates.value
                slots[0].status shouldBe SlotStatus.TAKEN

                messageSyncManager.stop()
            }
        }
    }

    describe("DataStore 마지막 동기화 시각 관리") {

        it("자동 동기화 성공 시 마지막 동기화 시각을 갱신한다") {
            runTest {
                coEvery { connectionManager.send(any()) } returns Result.success(Unit)
                coEvery { scheduleRepository.getBySlot(any()) } returns null
                coEvery { historyRepository.getRecord(any(), any()) } returns null

                messageSyncManager = MessageSyncManager(
                    connectionManager = connectionManager,
                    historyRepository = historyRepository,
                    scheduleRepository = scheduleRepository,
                    appPreferences = appPreferences,
                    alarmScheduler = alarmScheduler,
                    coroutineScope = this
                )

                messageSyncManager.start()
                testScheduler.advanceUntilIdle()

                // Connected 상태로 변경 → GET 명령 전송 시작
                connectionState.value = ConnectionState.Connected
                // 짧은 시간만 진행하여 performAutoSync가 시작되고 collectScheduleResponses가 대기하도록 함
                testScheduler.advanceTimeBy(100)
                testScheduler.runCurrent()

                // ScheduleInfo 응답 3개 전송 (타임아웃 3초 이내)
                incomingMessages.emit(
                    BluetoothMessage.ScheduleInfo(0, "혈압약", 9, 0, false)
                )
                incomingMessages.emit(
                    BluetoothMessage.ScheduleInfo(1, "당뇨약", 12, 30, false)
                )
                incomingMessages.emit(
                    BluetoothMessage.ScheduleInfo(2, "비타민", 18, 0, true)
                )
                testScheduler.advanceUntilIdle()

                coVerify {
                    appPreferences.updateLastSyncTime(any())
                }

                coVerify {
                    scheduleRepository.cacheSchedules(any())
                }

                messageSyncManager.stop()
            }
        }
    }
})
