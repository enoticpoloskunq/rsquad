package com.raccoonsquad.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raccoonsquad.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    
    companion object {
        private val THEME_KEY = stringPreferencesKey("app_theme")
        private val LAST_CONNECTED_ID = stringPreferencesKey("last_connected_id")
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
}
