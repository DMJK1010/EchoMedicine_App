package com.echomedicine.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echomedicine.app.data.db.dao.HistoryDao
import com.echomedicine.app.data.db.dao.ScheduleDao
import com.echomedicine.app.data.db.entity.HistoryEntity
import com.echomedicine.app.data.db.entity.ScheduleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room DAO 통합 테스트 (JUnit4 + Robolectric).
 *
 * In-memory Room DB로 ScheduleDao, HistoryDao의 CRUD 및 쿼리 정확성을 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomDaoIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var scheduleDao: ScheduleDao
    private lateinit var historyDao: HistoryDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        scheduleDao = db.scheduleDao()
        historyDao = db.historyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert 후 getBySlot으로 조회된다`() = runTest {
        scheduleDao.upsert(ScheduleEntity(0, "혈압약", 9, 0, 1000L))

        val loaded = scheduleDao.getBySlot(0)
        assertNotNull(loaded)
        assertEquals("혈압약", loaded!!.medicineName)
        assertEquals(9, loaded.hour)
    }

    @Test
    fun `같은 slotNumber로 upsert하면 덮어쓴다`() = runTest {
        scheduleDao.upsert(ScheduleEntity(0, "약A", 9, 0, 1000L))
        scheduleDao.upsert(ScheduleEntity(0, "약B", 10, 30, 2000L))

        val loaded = scheduleDao.getBySlot(0)
        assertEquals("약B", loaded!!.medicineName)
        assertEquals(10, loaded.hour)
    }

    @Test
    fun `upsertAll 후 전체 조회된다`() = runTest {
        scheduleDao.upsertAll(
            listOf(
                ScheduleEntity(0, "약A", 9, 0, 1000L),
                ScheduleEntity(1, "약B", 12, 0, 1000L),
                ScheduleEntity(2, "약C", 18, 0, 1000L)
            )
        )

        val all = scheduleDao.getAllSchedules().first()
        assertEquals(3, all.size)
        assertEquals(0, all[0].slotNumber)
        assertEquals("약C", all[2].medicineName)
    }

    @Test
    fun `getLastSyncTime은 최대 lastSyncedAt을 반환한다`() = runTest {
        scheduleDao.upsertAll(
            listOf(
                ScheduleEntity(0, "약A", 9, 0, 1000L),
                ScheduleEntity(1, "약B", 12, 0, 5000L)
            )
        )
        assertEquals(5000L, scheduleDao.getLastSyncTime())
    }

    @Test
    fun `history insert 후 getRecord로 조회된다`() = runTest {
        val today = 1_000_000L
        historyDao.insert(
            HistoryEntity(
                slotNumber = 0, medicineName = "혈압약",
                scheduledHour = 9, scheduledMinute = 0,
                actualTakenTime = today + 100, date = today, status = "TAKEN"
            )
        )

        val record = historyDao.getRecord(today, 0)
        assertNotNull(record)
        assertEquals("TAKEN", record!!.status)
    }

    @Test
    fun `getHistoryByDate는 해당 날짜의 기록만 반환한다`() = runTest {
        historyDao.insert(HistoryEntity(slotNumber = 0, medicineName = "A", scheduledHour = 9, scheduledMinute = 0, actualTakenTime = null, date = 100L, status = "MISSED"))
        historyDao.insert(HistoryEntity(slotNumber = 1, medicineName = "B", scheduledHour = 12, scheduledMinute = 0, actualTakenTime = null, date = 200L, status = "TAKEN"))

        val day100 = historyDao.getHistoryByDate(100L).first()
        assertEquals(1, day100.size)
        assertEquals("A", day100[0].medicineName)
    }

    @Test
    fun `복용률은 TAKEN 비율을 정확히 계산한다`() = runTest {
        // 3 TAKEN, 1 MISSED → 75%
        historyDao.insert(HistoryEntity(slotNumber = 0, medicineName = "A", scheduledHour = 9, scheduledMinute = 0, actualTakenTime = 1L, date = 100L, status = "TAKEN"))
        historyDao.insert(HistoryEntity(slotNumber = 1, medicineName = "B", scheduledHour = 9, scheduledMinute = 0, actualTakenTime = 1L, date = 100L, status = "TAKEN"))
        historyDao.insert(HistoryEntity(slotNumber = 2, medicineName = "C", scheduledHour = 9, scheduledMinute = 0, actualTakenTime = 1L, date = 100L, status = "TAKEN"))
        historyDao.insert(HistoryEntity(slotNumber = 0, medicineName = "D", scheduledHour = 9, scheduledMinute = 0, actualTakenTime = null, date = 100L, status = "MISSED"))

        assertEquals(75.0, historyDao.getTakenRateSince(0L).first(), 0.001)
    }

    @Test
    fun `기록이 없으면 복용률은 0이다`() = runTest {
        assertEquals(0.0, historyDao.getTakenRateSince(0L).first(), 0.001)
    }
}
