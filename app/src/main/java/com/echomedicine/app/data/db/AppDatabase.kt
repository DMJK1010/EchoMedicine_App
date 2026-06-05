package com.echomedicine.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.echomedicine.app.data.db.dao.HistoryDao
import com.echomedicine.app.data.db.dao.ScheduleDao
import com.echomedicine.app.data.db.entity.HistoryEntity
import com.echomedicine.app.data.db.entity.ScheduleEntity

@Database(
    entities = [ScheduleEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun historyDao(): HistoryDao
}
