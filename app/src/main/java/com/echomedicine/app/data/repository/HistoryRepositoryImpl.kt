package com.echomedicine.app.data.repository

import com.echomedicine.app.data.db.dao.HistoryDao
import com.echomedicine.app.data.db.entity.HistoryEntity
import com.echomedicine.app.domain.model.HistoryRecord
import com.echomedicine.app.domain.model.SlotStatus
import com.echomedicine.app.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao
) : HistoryRepository {

    override suspend fun recordTaken(
        slotNumber: Int,
        medicineName: String,
        scheduledHour: Int,
        scheduledMinute: Int
    ) {
        val now = System.currentTimeMillis()
        val today = todayMillis()
        val entity = HistoryEntity(
            slotNumber = slotNumber,
            medicineName = medicineName,
            scheduledHour = scheduledHour,
            scheduledMinute = scheduledMinute,
            actualTakenTime = now,
            date = today,
            status = STATUS_TAKEN
        )
        historyDao.insert(entity)
    }

    override suspend fun recordMissed(
        slotNumber: Int,
        medicineName: String,
        scheduledHour: Int,
        scheduledMinute: Int
    ) {
        val today = todayMillis()
        val entity = HistoryEntity(
            slotNumber = slotNumber,
            medicineName = medicineName,
            scheduledHour = scheduledHour,
            scheduledMinute = scheduledMinute,
            actualTakenTime = null,
            date = today,
            status = STATUS_MISSED
        )
        historyDao.insert(entity)
    }

    override fun getHistorySince(startDate: Long): Flow<List<HistoryRecord>> {
        return historyDao.getHistorySince(startDate).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getHistoryByDate(date: Long): Flow<List<HistoryRecord>> {
        return historyDao.getHistoryByDate(date).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTakenRate(startDate: Long): Flow<Double> {
        return historyDao.getTakenRateSince(startDate)
    }

    suspend fun getRecord(date: Long, slot: Int): HistoryRecord? {
        return historyDao.getRecord(date, slot)?.toDomain()
    }

    private fun HistoryEntity.toDomain(): HistoryRecord {
        return HistoryRecord(
            id = id,
            slotNumber = slotNumber,
            medicineName = medicineName,
            scheduledHour = scheduledHour,
            scheduledMinute = scheduledMinute,
            actualTakenTime = actualTakenTime,
            date = date,
            status = when (status) {
                STATUS_TAKEN -> SlotStatus.TAKEN
                STATUS_MISSED -> SlotStatus.MISSED
                else -> SlotStatus.MISSED
            }
        )
    }

    private fun todayMillis(): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    companion object {
        private const val STATUS_TAKEN = "TAKEN"
        private const val STATUS_MISSED = "MISSED"
    }
}
