package com.echomedicine.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.echomedicine.app.data.db.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(record: HistoryEntity)

    @Query("SELECT * FROM history WHERE date >= :startDate ORDER BY date DESC, slotNumber ASC")
    fun getHistorySince(startDate: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE date = :date ORDER BY slotNumber ASC")
    fun getHistoryByDate(date: Long): Flow<List<HistoryEntity>>

    @Query("""
        SELECT CASE
            WHEN COUNT(*) = 0 THEN 0.0
            ELSE SUM(CASE WHEN status = 'TAKEN' THEN 1.0 ELSE 0.0 END) / COUNT(*) * 100
        END
        FROM history
        WHERE date >= :startDate
    """)
    fun getTakenRateSince(startDate: Long): Flow<Double>

    @Query("SELECT * FROM history WHERE date = :date AND slotNumber = :slot LIMIT 1")
    suspend fun getRecord(date: Long, slot: Int): HistoryEntity?
}
