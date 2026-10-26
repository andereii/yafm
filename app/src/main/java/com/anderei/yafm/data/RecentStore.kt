package com.anderei.yafm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(
    val path: String,
    val name: String,
    val size: Long,
    val timestamp: Long,
)

class RecentStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("yafm_recents", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<RecentFile>> = _entries.asStateFlow()

    @Synchronized
    fun record(path: String, name: String, isDirectory: Boolean, size: Long) {
        if (isDirectory) return
        val entry = RecentFile(path, name, size, System.currentTimeMillis())
        val updated = (listOf(entry) + _entries.value.filterNot { it.path == path })
            .take(MAX_RECENTS)
        _entries.value = updated
        persist(updated)
    }

    @Synchronized
    fun remove(path: String) {
        val updated = _entries.value.filterNot { it.path == path }
        if (updated.size == _entries.value.size) return
        _entries.value = updated
        persist(updated)
    }

    private fun read(): List<RecentFile> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isEmpty()) continue
                add(
                    RecentFile(
                        path = path,
                        name = item.optString("name", path.substringAfterLast('/')),
                        size = item.optLong("size", 0L),
                        timestamp = item.optLong("timestamp", 0L),
                    ),
                )
            }
        }.sortedByDescending { it.timestamp }
    }.getOrDefault(emptyList())

    private fun persist(entries: List<RecentFile>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("path", entry.path)
                    .put("name", entry.name)
                    .put("size", entry.size)
                    .put("timestamp", entry.timestamp),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_RECENTS = 30
    }
}
