package com.astron.app.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astron.app.core.model.TransferHistoryRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.historyDataStore by preferencesDataStore(
    name = "astron_history",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class HistoryRepository(private val context: Context) {

    private val KEY_HISTORY_JSON = stringPreferencesKey("transfer_history_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cachedList: MutableList<TransferHistoryRecord>? = null

    val historyFlow: Flow<List<TransferHistoryRecord>> = context.historyDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val rawJson = prefs[KEY_HISTORY_JSON] ?: "[]"
            val parsed = try {
                json.decodeFromString<List<TransferHistoryRecord>>(rawJson).sortedByDescending { it.timestamp }
            } catch (_: Throwable) {
                emptyList()
            }
            mutex.withLock {
                cachedList = parsed.toMutableList()
            }
            parsed
        }

    suspend fun addRecord(record: TransferHistoryRecord) = mutex.withLock {
        val list = (cachedList ?: emptyList()).toMutableList()
        list.removeAll { it.id == record.id }
        list.add(0, record)
        val trimmed = list.take(100)
        cachedList = trimmed.toMutableList()
        try {
            context.historyDataStore.edit { prefs ->
                prefs[KEY_HISTORY_JSON] = json.encodeToString(trimmed)
            }
        } catch (_: Throwable) {}
    }

    suspend fun deleteRecord(id: String) = mutex.withLock {
        val list = (cachedList ?: emptyList()).filter { it.id != id }
        cachedList = list.toMutableList()
        try {
            context.historyDataStore.edit { prefs ->
                prefs[KEY_HISTORY_JSON] = json.encodeToString(list)
            }
        } catch (_: Throwable) {}
    }

    suspend fun clearHistory() = mutex.withLock {
        cachedList = mutableListOf()
        try {
            context.historyDataStore.edit { prefs ->
                prefs[KEY_HISTORY_JSON] = "[]"
            }
        } catch (_: Throwable) {}
    }
}
