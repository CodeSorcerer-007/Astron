package com.astron.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astron.app.core.model.TransferHistoryRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.historyDataStore by preferencesDataStore(name = "astron_history")

class HistoryRepository(private val context: Context) {

    private val KEY_HISTORY_JSON = stringPreferencesKey("transfer_history_json")
    private val json = Json { ignoreUnknownKeys = true }

    val historyFlow: Flow<List<TransferHistoryRecord>> = context.historyDataStore.data.map { prefs ->
        val rawJson = prefs[KEY_HISTORY_JSON] ?: "[]"
        try {
            json.decodeFromString<List<TransferHistoryRecord>>(rawJson).sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addRecord(record: TransferHistoryRecord) {
        context.historyDataStore.edit { prefs ->
            val currentJson = prefs[KEY_HISTORY_JSON] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<TransferHistoryRecord>>(currentJson).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            currentList.add(0, record)
            // Limit history to 100 recent transfers to stay lightweight
            val trimmed = currentList.take(100)
            prefs[KEY_HISTORY_JSON] = json.encodeToString(trimmed)
        }
    }

    suspend fun deleteRecord(id: String) {
        context.historyDataStore.edit { prefs ->
            val currentJson = prefs[KEY_HISTORY_JSON] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<TransferHistoryRecord>>(currentJson).filter { it.id != id }
            } catch (_: Exception) {
                emptyList()
            }
            prefs[KEY_HISTORY_JSON] = json.encodeToString(currentList)
        }
    }

    suspend fun clearHistory() {
        context.historyDataStore.edit { prefs ->
            prefs[KEY_HISTORY_JSON] = "[]"
        }
    }
}
