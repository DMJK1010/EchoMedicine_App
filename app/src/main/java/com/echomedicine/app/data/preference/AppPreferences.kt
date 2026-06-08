package com.echomedicine.app.data.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore Preferences를 활용한 앱 설정 관리.
 *
 * 마지막 동기화 시각, 다크 모드 설정 등 경량 설정값을 관리한다.
 */

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "echo_medicine_prefs")

@Singleton
class AppPreferences @Inject constructor(
    private val context: Context
) {

    companion object {
        private val KEY_LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        private val KEY_DARK_MODE = intPreferencesKey("dark_mode")

        /** 다크 모드: 시스템 설정 따름 */
        const val DARK_MODE_SYSTEM = 0
        /** 다크 모드: 항상 라이트 */
        const val DARK_MODE_LIGHT = 1
        /** 다크 모드: 항상 다크 */
        const val DARK_MODE_DARK = 2
    }

    /**
     * 마지막 동기화 시각을 Flow로 관찰한다.
     */
    val lastSyncTime: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC_TIME]
    }

    /**
     * 다크 모드 설정을 Flow로 관찰한다. (기본값: 시스템 설정 따름)
     */
    val darkMode: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE] ?: DARK_MODE_SYSTEM
    }

    /**
     * 다크 모드 설정을 갱신한다.
     *
     * @param mode DARK_MODE_SYSTEM / DARK_MODE_LIGHT / DARK_MODE_DARK
     */
    suspend fun updateDarkMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = mode
        }
    }

    /**
     * 마지막 동기화 시각을 갱신한다.
     *
     * @param timeMillis Unix timestamp (밀리초)
     */
    suspend fun updateLastSyncTime(timeMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_TIME] = timeMillis
        }
    }

    /**
     * 마지막 동기화 시각을 초기화한다.
     */
    suspend fun clearLastSyncTime() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_LAST_SYNC_TIME)
        }
    }
}
