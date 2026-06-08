package com.echomedicine.app.data.repository

import com.echomedicine.app.data.db.dao.ScheduleDao
import com.echomedicine.app.data.db.entity.ScheduleEntity
import com.echomedicine.app.domain.model.Schedule
import com.echomedicine.app.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ScheduleRepository {

    override fun getSchedules(): Flow<List<Schedule>> {
        return scheduleDao.getAllSchedules().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun cacheSchedules(schedules: List<Schedule>) {
        val now = System.currentTimeMillis()
        val entities = schedules.map { it.toEntity(syncedAt = now) }
        scheduleDao.upsertAll(entities)
    }

    override suspend fun cacheSchedule(schedule: Schedule) {
        scheduleDao.upsert(schedule.toEntity(syncedAt = System.currentTimeMillis()))
    }

    override suspend fun getLastSyncTime(): Long? {
        return scheduleDao.getLastSyncTime()
    }

    suspend fun getBySlot(slot: Int): Schedule? {
        return scheduleDao.getBySlot(slot)?.toDomain()
    }

    private fun ScheduleEntity.toDomain(): Schedule {
        return Schedule(
            slotNumber = slotNumber,
            medicineName = medicineName,
            hour = hour,
            minute = minute
        )
    }

    private fun Schedule.toEntity(syncedAt: Long): ScheduleEntity {
        return ScheduleEntity(
            slotNumber = slotNumber,
            medicineName = medicineName,
            hour = hour,
            minute = minute,
            lastSyncedAt = syncedAt
        )
    }
}
