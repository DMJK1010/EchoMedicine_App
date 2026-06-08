package com.echomedicine.app.domain.repository

import com.echomedicine.app.domain.model.Schedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun getSchedules(): Flow<List<Schedule>>
    suspend fun cacheSchedules(schedules: List<Schedule>)
    suspend fun cacheSchedule(schedule: Schedule)
    suspend fun getLastSyncTime(): Long?
}
