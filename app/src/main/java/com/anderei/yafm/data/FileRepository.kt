package com.anderei.yafm.data

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class OperationResult(
    val succeeded: Int,
    val failed: Int,
)

/** Standard scoped-storage backend based on [java.io.File]. */
class FileRepository(
    override val navigationRoot: File = Environment.getExternalStorageDirectory(),
) : FileBackend {

    override suspend fun list(directory: File): List<FileItem> = withContext(Dispatchers.IO) {
        val children = directory.listFiles()
            ?: throw IOException("Unable to read this folder")
        children.map(FileItem::from)
    }

    override suspend fun createFolder(parent: File, name: String): File =
        withContext(Dispatchers.IO) {
            val target = File(parent, name)
            if (target.exists()) {
                throw IOException("An item named \"$name\" already exists")
            }
            if (!target.mkdirs()) {
                throw IOException("Couldn't create folder")
            }
            target
        }

    override suspend fun rename(source: File, newName: String): File =
        withContext(Dispatchers.IO) {
            val parent = source.parentFile ?: throw IOException("This item has no parent folder")
            val target = File(parent, newName)
            if (target.absolutePath != source.absolutePath && target.exists()) {
                throw IOException("An item named \"$newName\" already exists")
            }
            if (!source.renameTo(target)) {
                throw IOException("Couldn't rename this item")
            }
            target
        }

    override suspend fun delete(files: List<File>): OperationResult = withContext(Dispatchers.IO) {
        var succeeded = 0
        var failed = 0
        files.forEach { file ->
            val deleted = runCatching {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }.getOrDefault(false)
            if (deleted && !file.exists()) succeeded++ else failed++
        }
        OperationResult(succeeded, failed)
    }

    override suspend fun copy(files: List<File>, targetDirectory: File): OperationResult =
        withContext(Dispatchers.IO) {
            var succeeded = 0
            var failed = 0
            files.forEach { source ->
                if (!source.exists() || isInside(source, targetDirectory)) {
                    failed++
                    return@forEach
                }
                val destination = uniqueChild(targetDirectory, source.name, source.isDirectory)
                if (copyRecursive(source, destination)) succeeded++ else failed++
            }
            OperationResult(succeeded, failed)
        }

    override suspend fun move(files: List<File>, targetDirectory: File): OperationResult =
        withContext(Dispatchers.IO) {
            var succeeded = 0
            var failed = 0
            files.forEach { source ->
                if (!source.exists() || isInside(source, targetDirectory)) {
                    failed++
                    return@forEach
                }
                val destination = uniqueChild(targetDirectory, source.name, source.isDirectory)
                if (moveRecursive(source, destination)) succeeded++ else failed++
            }
            OperationResult(succeeded, failed)
        }

    override suspend fun stage(source: File, stagingDirectory: File): File =
        withContext(Dispatchers.IO) {
            if (source.canRead()) {
                source
            } else {
                throw IOException("This file needs Shizuku or root access")
            }
        }
}

/** True when [child] is [parent] itself or lives somewhere below it. */
internal fun isInside(child: File, parent: File): Boolean {
    val childPath = child.absolutePath
    val parentPath = parent.absolutePath
    return parentPath == "/" ||
        childPath == parentPath ||
        childPath.startsWith(parentPath + File.separator)
}

/** Finds a non-clashing name, appending " (1)", " (2)", ... before the extension. */
internal fun uniqueName(
    name: String,
    isDirectory: Boolean,
    exists: (String) -> Boolean,
): String {
    if (!exists(name)) return name

    val dot = name.lastIndexOf('.')
    val hasExtension = !isDirectory && dot > 0
    val base = if (hasExtension) name.substring(0, dot) else name
    val extension = if (hasExtension) name.substring(dot) else ""

    var index = 1
    while (true) {
        val candidate = "$base ($index)$extension"
        if (!exists(candidate)) return candidate
        index++
    }
}

/** Returns a destination inside [directory] that does not clash with an existing name. */
internal fun uniqueChild(directory: File, name: String, isDirectory: Boolean): File {
    val selected = uniqueName(name, isDirectory) { candidate ->
        File(directory, candidate).exists()
    }
    return File(directory, selected)
}

private fun copyRecursive(source: File, destination: File): Boolean {
    if (source.isDirectory) {
        if (!destination.exists() && !destination.mkdirs()) return false
        val children = source.listFiles() ?: return false
        var allCopied = true
        children.forEach { child ->
            if (!copyRecursive(child, File(destination, child.name))) allCopied = false
        }
        return allCopied
    }
    return runCatching {
        source.copyTo(destination, overwrite = false)
        true
    }.getOrDefault(false)
}

private fun moveRecursive(source: File, destination: File): Boolean {
    if (source.renameTo(destination)) return true
    return copyRecursive(source, destination) && source.deleteRecursively()
}
