package com.echomedicine.app.domain.repository

import com.echomedicine.app.domain.model.HistoryRecord
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    suspend fun recordTaken(slotNumber: Int, medicineName: String, scheduledHour: Int, scheduledMinute: Int)
    suspend fun recordMissed(slotNumber: Int, medicineName: String, scheduledHour: Int, scheduledMinute: Int)
    suspend fun getRecord(date: Long, slot: Int): HistoryRecord?
    fun getHistorySince(startDate: Long): Flow<List<HistoryRecord>>
    fun getHistoryByDate(date: Long): Flow<List<HistoryRecord>>
    fun getTakenRate(startDate: Long): Flow<Double>
}
