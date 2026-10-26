package com.anderei.yafm.data.archive

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import java.io.File

data class ArchiveFileInfo(
    val file: File,
    val displayPath: String,
    val format: ArchiveFormat,
) {
    val name: String get() = file.name
    val size: Long get() = file.length()
    val lastModified: Long get() = file.lastModified()
}

class ArchiveScanner {

    suspend fun scan(
        roots: List<File>,
        skipPaths: Set<String> = emptySet(),
        onProgress: (scanned: Int) -> Unit = {},
    ): List<ArchiveFileInfo> {
        val result = mutableListOf<ArchiveFileInfo>()
        val queue = ArrayDeque<File>()
        roots.filter { it.isDirectory }.forEach(queue::add)
        val visited = HashSet<String>()
        var scanned = 0

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directory = queue.removeFirst()
            val path = directory.absolutePath
            if (skipPaths.any { path == it || path.startsWith(it + File.separator) }) continue
            if (directory.name in SKIP_DIRECTORIES || directory.name.startsWith(".")) continue
            val canonical = runCatching { directory.canonicalPath }.getOrDefault(path)
            if (!visited.add(canonical)) continue

            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                scanned++
                if (scanned % PROGRESS_EVERY == 0) {
                    onProgress(scanned)
                    yield()
                }
                if (child.isDirectory) {
                    if (!child.name.startsWith(".") && child.name !in SKIP_DIRECTORIES) {
                        queue.add(child)
                    }
                } else if (!child.name.startsWith(".")) {
                    val format = ArchiveFormat.fromName(child.name)
                    if (format != null && format.canExtract) {
                        result += ArchiveFileInfo(
                            file = child,
                            displayPath = child.archiveDisplayPath(),
                            format = format,
                        )
                    }
                }
            }
        }
        onProgress(scanned)
        return result.sortedBy { it.displayPath.lowercase() }
    }

    private companion object {
        const val PROGRESS_EVERY = 200
        val SKIP_DIRECTORIES = setOf(
            ".thumbnails",
            ".trashed",
            ".trash",
            "lost+found",
            "LOST.DIR",
            "System Volume Information",
            "\$RECYCLE.BIN",
        )
    }
}
