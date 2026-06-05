package com.echomedicine.app.di

import com.echomedicine.app.data.repository.HistoryRepositoryImpl
import com.echomedicine.app.data.repository.ScheduleRepositoryImpl
import com.echomedicine.app.domain.repository.HistoryRepository
import com.echomedicine.app.domain.repository.ScheduleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(
        impl: ScheduleRepositoryImpl
    ): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        impl: HistoryRepositoryImpl
    ): HistoryRepository
}
