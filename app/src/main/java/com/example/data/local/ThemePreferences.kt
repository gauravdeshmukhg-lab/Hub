package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FeedTypeFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class ThemePreferences(private val context: Context) {
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val VISIBLE_FEED_TYPES_KEY = stringSetPreferencesKey("visible_feed_types")
        private val FOCUS_MODE_KEY = booleanPreferencesKey("focus_mode_enabled")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val visibleFeedTypes: Flow<Set<FeedTypeFilter>> = context.dataStore.data.map { preferences ->
        val saved = preferences[VISIBLE_FEED_TYPES_KEY]
        if (saved == null) {
            FeedTypeFilter.entries.toSet()
        } else {
            saved.mapNotNull { name ->
                runCatching { FeedTypeFilter.valueOf(name) }.getOrNull()
            }.toSet()
        }
    }

    val isFocusModeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FOCUS_MODE_KEY] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setVisibleFeedTypes(types: Set<FeedTypeFilter>) {
        context.dataStore.edit { preferences ->
            preferences[VISIBLE_FEED_TYPES_KEY] = types.map { it.name }.toSet()
        }
    }

    suspend fun setFocusModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FOCUS_MODE_KEY] = enabled
        }
    }
}
