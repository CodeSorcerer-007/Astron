package com.astron.app.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astron.app.core.model.TransferPriority
import com.astron.app.core.util.NetworkUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(
    name = "astron_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class PreferencesRepository(private val context: Context) {

    private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
    private val KEY_TRANSFER_PRIORITY = stringPreferencesKey("transfer_priority")
    private val KEY_AUTO_ACCEPT_TRUSTED = booleanPreferencesKey("auto_accept_trusted")
    private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")

    val deviceNameFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            prefs[KEY_DEVICE_NAME] ?: NetworkUtils.getDefaultDeviceName()
        }

    val transferPriorityFlow: Flow<TransferPriority> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val raw = prefs[KEY_TRANSFER_PRIORITY]
            if (raw != null) {
                try { TransferPriority.valueOf(raw) } catch (_: Exception) { TransferPriority.BALANCED }
            } else {
                TransferPriority.BALANCED
            }
        }

    val autoAcceptTrustedFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            prefs[KEY_AUTO_ACCEPT_TRUSTED] ?: false
        }

    val hapticsEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            prefs[KEY_HAPTICS_ENABLED] ?: true
        }

    val darkModeFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            prefs[KEY_DARK_MODE] ?: false
        }

    suspend fun setDeviceName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVICE_NAME] = name.trim()
        }
    }

    suspend fun setTransferPriority(priority: TransferPriority) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TRANSFER_PRIORITY] = priority.name
        }
    }

    suspend fun setAutoAcceptTrusted(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_ACCEPT_TRUSTED] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = enabled
        }
    }
}
