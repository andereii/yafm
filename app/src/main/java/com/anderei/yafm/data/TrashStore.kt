package com.anderei.yafm.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

data class TrashEntry(
    val id: String,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val deletedAt: Long,
    val expiresAt: Long,
)

data class TrashResult(val moved: Int, val failed: Int)

class TrashStore(
    context: Context,
    private val settings: SettingsStore,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("yafm_trash", Context.MODE_PRIVATE)
    private val trashRoot = File(
        appContext.getExternalFilesDir(null) ?: appContext.filesDir,
        "trash",
    )

    val directory: File get() = trashRoot

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<TrashEntry>> = _entries.asStateFlow()

    init {
        runCatching { trashRoot.mkdirs() }
    }

    /**
     * Moves the selected files into the app's trash and records the original locations.
     * Uses the active backend, so Shizuku/root items are handled as well as normal storage.
     */
    suspend fun moveToTrash(files: List<File>, backend: FileBackend): TrashResult =
        withContext(Dispatchers.IO) {
            if (files.isEmpty()) return@withContext TrashResult(0, 0)
            runCatching { trashRoot.mkdirs() }

            val days = settings.trashRetentionDays.value
            val added = mutableListOf<TrashEntry>()
            var failed = 0

            files.forEach { file ->
                val id = UUID.randomUUID().toString()
                val entryDir = File(trashRoot, id)
                if (!entryDir.mkdirs()) {
                    failed++
                    return@forEach
                }

                val result = runCatching { backend.move(listOf(file), entryDir) }
                    .getOrElse { entryDir.deleteRecursively(); failed++; return@forEach }
                val trashed = entryDir.listFiles()?.firstOrNull()
                if (result.succeeded != 1 || trashed == null) {
                    entryDir.deleteRecursively()
                    failed++
                    return@forEach
                }

                val now = System.currentTimeMillis()
                added += TrashEntry(
                    id = id,
                    originalPath = file.absolutePath,
                    trashPath = trashed.absolutePath,
                    name = trashed.name,
                    isDirectory = trashed.isDirectory,
                    size = if (trashed.isDirectory) 0L else trashed.length(),
                    deletedAt = now,
                    expiresAt = now + days * DAY_MILLIS,
                )
            }

            if (added.isNotEmpty()) {
                _entries.update { current ->
                    (added + current).sortedByDescending { it.deletedAt }
                }
                persist()
            }
            TrashResult(added.size, failed)
        }

    suspend fun restore(entry: TrashEntry, backend: FileBackend): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trashed = File(entry.trashPath)
            if (!trashed.exists()) {
                removeEntry(entry)
                return@withContext Result.failure(IOException("The trashed file no longer exists"))
            }
            val parent = File(entry.originalPath).parentFile
                ?: return@withContext Result.failure(IOException("The original folder is unknown"))

            // Don't stat the parent locally: it may be a root-only path that only the
            // privileged backend can reach. The move reports a missing parent itself.
            val result = runCatching { backend.move(listOf(trashed), parent) }
                .getOrElse { return@withContext Result.failure(it) }
            if (result.succeeded != 1) {
                return@withContext Result.failure(IOException("Couldn't restore this item"))
            }

            File(entry.trashPath).parentFile?.deleteRecursively()
            removeEntry(entry)
            Result.success(Unit)
        }

    suspend fun deleteForever(entry: TrashEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trashed = File(entry.trashPath)
            val deleted = runCatching { trashed.parentFile?.deleteRecursively() ?: trashed.deleteRecursively() }
                .getOrDefault(false)
            removeEntry(entry)
            if (deleted || !trashed.exists()) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Couldn't delete this item"))
            }
        }

    suspend fun empty(): Result<Unit> = withContext(Dispatchers.IO) {
        val deleted = runCatching { trashRoot.deleteRecursively() }.getOrDefault(false)
        runCatching { trashRoot.mkdirs() }
        _entries.value = emptyList()
        persist()
        if (deleted || !trashRoot.exists()) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("Couldn't empty the Recycle Bin"))
        }
    }

    /** Deletes entries whose retention period has elapsed. */
    suspend fun cleanupExpired(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val expired = _entries.value.filter { it.expiresAt <= now }
        if (expired.isEmpty()) return@withContext 0

        expired.forEach { entry ->
            File(entry.trashPath).parentFile?.deleteRecursively()
        }
        _entries.update { current ->
            current.filterNot { it.expiresAt <= now }
        }
        persist()
        expired.size
    }

    /** Recomputes expiry for existing items and immediately cleans up newly expired ones. */
    suspend fun updateRetention(days: Int) {
        _entries.update { current ->
            current.map { entry ->
                entry.copy(expiresAt = entry.deletedAt + days * DAY_MILLIS)
            }
        }
        persist()
        cleanupExpired()
    }

    private fun removeEntry(entry: TrashEntry) {
        _entries.update { current -> current.filterNot { it.id == entry.id } }
        persist()
    }

    private fun read(): List<TrashEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val trashPath = item.optString("trashPath")
                if (id.isEmpty() || trashPath.isEmpty()) continue
                add(
                    TrashEntry(
                        id = id,
                        originalPath = item.optString("originalPath"),
                        trashPath = trashPath,
                        name = item.optString("name", trashPath.substringAfterLast('/')),
                        isDirectory = item.optBoolean("isDirectory", false),
                        size = item.optLong("size", 0L),
                        deletedAt = item.optLong("deletedAt", 0L),
                        expiresAt = item.optLong("expiresAt", 0L),
                    ),
                )
            }
        }.sortedByDescending { it.deletedAt }
    }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray()
        _entries.value.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("originalPath", entry.originalPath)
                    .put("trashPath", entry.trashPath)
                    .put("name", entry.name)
                    .put("isDirectory", entry.isDirectory)
                    .put("size", entry.size)
                    .put("deletedAt", entry.deletedAt)
                    .put("expiresAt", entry.expiresAt),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
