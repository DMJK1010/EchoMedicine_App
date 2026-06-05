package com.echomedicine.app.domain.usecase

import com.echomedicine.app.domain.model.Schedule
import com.echomedicine.app.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    operator fun invoke(): Flow<List<Schedule>> {
        return scheduleRepository.getSchedules()
    }
}
