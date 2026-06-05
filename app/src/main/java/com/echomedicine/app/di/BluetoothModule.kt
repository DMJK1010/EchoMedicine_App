package com.echomedicine.app.di

import com.echomedicine.app.data.bluetooth.BluetoothConnectionManager
import com.echomedicine.app.data.bluetooth.BluetoothConnectionManagerImpl
import com.echomedicine.app.data.repository.BluetoothRepositoryImpl
import com.echomedicine.app.domain.repository.BluetoothRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothModule {

    @Binds
    @Singleton
    abstract fun bindBluetoothConnectionManager(
        impl: BluetoothConnectionManagerImpl
    ): BluetoothConnectionManager

    @Binds
    @Singleton
    abstract fun bindBluetoothRepository(
        impl: BluetoothRepositoryImpl
    ): BluetoothRepository
}
