package com.echomedicine.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echomedicine.app.data.db.AppDatabase
import com.echomedicine.app.domain.model.Schedule
import com.echomedicine.app.domain.model.SlotStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * Repository 통합 테스트 (JUnit4 + Robolectric).
 *
 * In-memory Room DB와 실제 Repository 구현체를 결합하여
 * 스케줄 캐싱/조회, 복용 이력 저장/복용률 계산이 올바른지 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepositoryIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var scheduleRepository: ScheduleRepositoryImpl
    private lateinit var historyRepository: HistoryRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        scheduleRepository = ScheduleRepositoryImpl(db.scheduleDao())
        historyRepository = HistoryRepositoryImpl(db.historyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `cacheSchedule로 단일 저장 후 getBySlot 조회된다`() = runTest {
        scheduleRepository.cacheSchedule(Schedule(0, "혈압약", 9, 0))

        val loaded = scheduleRepository.getBySlot(0)
        assertNotNull(loaded)
        assertEquals("혈압약", loaded!!.medicineName)
    }

    @Test
    fun `cacheSchedules로 여러 건 저장 후 getSchedules로 관찰된다`() = runTest {
        scheduleRepository.cacheSchedules(
            listOf(
                Schedule(0, "약A", 9, 0),
                Schedule(1, "약B", 12, 30)
            )
        )

        assertEquals(2, scheduleRepository.getSchedules().first().size)
    }

    @Test
    fun `recordTaken 후 TAKEN 상태로 조회된다`() = runTest {
        historyRepository.recordTaken(0, "혈압약", 9, 0)

        val record = historyRepository.getRecord(todayMillis(), 0)
        assertNotNull(record)
        assertEquals(SlotStatus.TAKEN, record!!.status)
        assertNotNull(record.actualTakenTime)
    }

    @Test
    fun `recordMissed 후 MISSED 상태로 조회되고 실제 복용 시간은 null이다`() = runTest {
        historyRepository.recordMissed(1, "당뇨약", 12, 30)

        val record = historyRepository.getRecord(todayMillis(), 1)
        assertNotNull(record)
        assertEquals(SlotStatus.MISSED, record!!.status)
        assertNull(record.actualTakenTime)
    }

    @Test
    fun `복용률은 TAKEN 전체 비율로 계산된다`() = runTest {
        historyRepository.recordTaken(0, "A", 9, 0)
        historyRepository.recordMissed(1, "B", 12, 0)

        // 1 TAKEN, 1 MISSED → 50%
        assertEquals(50.0, historyRepository.getTakenRate(0L).first(), 0.001)
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
