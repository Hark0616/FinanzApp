package com.ivan.finanzapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance_prefs")

class AppearancePrefs @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    val useDynamicColor: Flow<Boolean> = context.appearanceDataStore.data.map { prefs ->
        prefs[USE_DYNAMIC_COLOR] ?: false
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.appearanceDataStore.edit { prefs ->
            prefs[USE_DYNAMIC_COLOR] = enabled
        }
    }

    private companion object {
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }
}
