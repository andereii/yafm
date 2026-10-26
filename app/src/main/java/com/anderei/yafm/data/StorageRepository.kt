package com.anderei.yafm.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

data class StorageVolumeInfo(
    val id: String,
    val root: File,
    val description: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
)

data class StorageUsage(
    val usedBytes: Long,
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else {
            (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}

class StorageRepository(context: Context) {

    private val appContext = context.applicationContext

    fun volumes(): List<StorageVolumeInfo> {
        val manager = appContext.getSystemService(StorageManager::class.java)
        val listed = manager?.storageVolumes.orEmpty().mapNotNull { volume ->
            val directory = runCatching { volume.directory }.getOrNull()
                ?: return@mapNotNull null
            val state = volume.state
            if (state != Environment.MEDIA_MOUNTED &&
                state != Environment.MEDIA_MOUNTED_READ_ONLY
            ) {
                return@mapNotNull null
            }
            val description = runCatching {
                volume.getDescription(appContext)
            }.getOrNull().orEmpty()
            StorageVolumeInfo(
                id = volume.uuid ?: directory.absolutePath,
                root = directory,
                description = description,
                isPrimary = volume.isPrimary,
                isRemovable = volume.isRemovable,
            )
        }.distinctBy { it.root.absolutePath }

        if (listed.isNotEmpty()) {
            return listed.sortedWith(
                compareByDescending<StorageVolumeInfo> { it.isPrimary }
                    .thenBy { it.description },
            )
        }

        // Extremely unlikely fallback (storage service unavailable).
        val internal = Environment.getExternalStorageDirectory()
        return listOf(
            StorageVolumeInfo(
                id = "primary",
                root = internal,
                description = "",
                isPrimary = true,
                isRemovable = false,
            ),
        )
    }

    fun usage(root: File): StorageUsage? = runCatching {
        val stat = StatFs(root.absolutePath)
        val total = stat.totalBytes
        val available = stat.availableBytes
        StorageUsage(
            usedBytes = (total - available).coerceAtLeast(0L),
            totalBytes = total,
            availableBytes = available,
        )
    }.getOrNull()

    fun volumeFor(path: File): StorageVolumeInfo? {
        val pathString = path.absolutePath
        return volumes()
            .sortedByDescending { it.root.absolutePath.length }
            .firstOrNull { volume ->
                val root = volume.root.absolutePath
                pathString == root ||
                    pathString.startsWith(root.trimEnd('/') + File.separator)
            }
    }

    fun internalVolume(): StorageVolumeInfo? =
        volumes().firstOrNull { it.isPrimary } ?: volumes().firstOrNull()
}
