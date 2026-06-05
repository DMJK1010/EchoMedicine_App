package com.echomedicine.app.domain.usecase

import com.echomedicine.app.domain.model.HistoryRecord
import com.echomedicine.app.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    operator fun invoke(startDate: Long): Flow<List<HistoryRecord>> {
        return historyRepository.getHistorySince(startDate)
    }
}
