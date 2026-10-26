package com.anderei.yafm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val addedAt: Long,
)

class BookmarkStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("yafm_bookmarks", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<Bookmark>> = _entries.asStateFlow()

    fun isBookmarked(path: String): Boolean = _entries.value.any { it.path == path }

    @Synchronized
    fun toggle(path: String, name: String, isDirectory: Boolean): Boolean {
        val current = _entries.value
        val existing = current.any { it.path == path }
        val updated = if (existing) {
            current.filterNot { it.path == path }
        } else {
            (listOf(Bookmark(path, name, isDirectory, System.currentTimeMillis())) + current)
                .take(MAX_BOOKMARKS)
        }
        _entries.value = updated
        persist(updated)
        return !existing
    }

    @Synchronized
    fun remove(path: String) {
        val updated = _entries.value.filterNot { it.path == path }
        _entries.value = updated
        persist(updated)
    }

    private fun read(): List<Bookmark> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isEmpty()) continue
                add(
                    Bookmark(
                        path = path,
                        name = item.optString("name", path.substringAfterLast('/')),
                        isDirectory = item.optBoolean("isDirectory", false),
                        addedAt = item.optLong("addedAt", 0L),
                    ),
                )
            }
        }.sortedByDescending { it.addedAt }
    }.getOrDefault(emptyList())

    private fun persist(entries: List<Bookmark>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("path", entry.path)
                    .put("name", entry.name)
                    .put("isDirectory", entry.isDirectory)
                    .put("addedAt", entry.addedAt),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_BOOKMARKS = 100
    }
}
