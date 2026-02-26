package com.raccoonsquad.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raccoonsquad.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    
    companion object {
        private val THEME_KEY = stringPreferencesKey("app_theme")
        private val LAST_CONNECTED_ID = stringPreferencesKey("last_connected_id")
        private val SUCCESSFUL_CONNECTIONS = intPreferencesKey("successful_connections")
        private val RATING_SHOWN = booleanPreferencesKey("rating_shown")
        private val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        private val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        private val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")

        const val RATING_THRESHOLD = 5  // Show rating after 5 successful connections
    }
    
    val theme: Flow<AppTheme> = context.dataStore.data
        .map { preferences ->
            val themeName = preferences[THEME_KEY] ?: AppTheme.PURPLE.name
            try {
                AppTheme.valueOf(themeName)
            } catch (e: Exception) {
                AppTheme.PURPLE
            }
        }
    
    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }
    
    val lastConnectedId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_CONNECTED_ID]
        }
    
    suspend fun setLastConnectedId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id != null) {
                preferences[LAST_CONNECTED_ID] = id
            } else {
                preferences.remove(LAST_CONNECTED_ID)
            }
        }
    }
    
    // Smart Rating
    val successfulConnections: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SUCCESSFUL_CONNECTIONS] ?: 0
        }
    
    val ratingShown: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[RATING_SHOWN] ?: false
        }
    
    suspend fun incrementSuccessfulConnections() {
        context.dataStore.edit { preferences ->
            val current = preferences[SUCCESSFUL_CONNECTIONS] ?: 0
            preferences[SUCCESSFUL_CONNECTIONS] = current + 1
        }
    }
    
    suspend fun setRatingShown(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RATING_SHOWN] = shown
        }
    }
    
    suspend fun resetConnectionCounter() {
        context.dataStore.edit { preferences ->
            preferences[SUCCESSFUL_CONNECTIONS] = 0
        }
    }

    // Developer Mode - shows all logs vs simplified view
    val developerMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DEVELOPER_MODE] ?: false
        }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEVELOPER_MODE] = enabled
        }
    }

    // Kill Switch - block internet when VPN disconnects unexpectedly
    val killSwitch: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KILL_SWITCH] ?: false
        }

    suspend fun setKillSwitch(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KILL_SWITCH] = enabled
        }
    }

    // Auto-reconnect - automatically reconnect on connection drop
    val autoReconnect: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_RECONNECT] ?: true  // Enabled by default
        }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_RECONNECT] = enabled
        }
    }
}
