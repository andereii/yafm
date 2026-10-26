package com.anderei.yafm.ui.explorer

import com.anderei.yafm.data.FileItem
import java.io.File

data class Crumb(val label: String, val directory: File)

/** True when [path] is [ancestor] itself or lives below it. */
fun isWithin(path: File, ancestor: File): Boolean {
    val pathString = path.absolutePath
    val ancestorString = ancestor.absolutePath
    return ancestorString == "/" ||
        pathString == ancestorString ||
        pathString.startsWith(ancestorString + File.separator)
}

/**
 * Builds breadcrumbs for the current directory.
 *
 * With root access the chain can start at "/" and the shared-storage directory is still shown as
 * "Internal storage" instead of "storage > emulated > 0".
 */
fun buildCrumbs(
    home: File,
    current: File,
    includeFilesystemRoot: Boolean,
    root: File = home,
    rootLabel: String = "Internal storage",
): List<Crumb> {
    val crumbs = mutableListOf<Crumb>()
    if (includeFilesystemRoot) {
        crumbs += Crumb("Root", File("/"))
    }

    when {
        isWithin(current, home) -> {
            crumbs += Crumb("Internal storage", home)
            appendSegments(crumbs, home, current)
        }

        includeFilesystemRoot -> appendSegments(crumbs, File("/"), current)

        isWithin(current, root) -> {
            crumbs += Crumb(rootLabel, root)
            appendSegments(crumbs, root, current)
        }

        else -> crumbs += Crumb(current.name.ifBlank { "Storage" }, current)
    }

    return crumbs
}

private fun appendSegments(crumbs: MutableList<Crumb>, base: File, current: File) {
    if (base.absolutePath == current.absolutePath) return

    val relative = current.absolutePath
        .removePrefix(base.absolutePath)
        .trim(File.separatorChar)
    var directory = base
    relative.split(File.separatorChar)
        .filter { it.isNotEmpty() }
        .forEach { part ->
            directory = File(directory, part)
            crumbs += Crumb(part, directory)
        }
}

fun sortEntries(
    entries: List<FileItem>,
    sort: SortOption,
    ascending: Boolean,
): List<FileItem> {
    val comparator: Comparator<FileItem> = when (sort) {
        SortOption.NAME -> compareBy { it.name.lowercase() }
        SortOption.DATE -> compareBy<FileItem> { it.lastModified }.thenBy { it.name.lowercase() }
        SortOption.SIZE -> compareBy<FileItem> { it.size }.thenBy { it.name.lowercase() }
        SortOption.TYPE -> compareBy<FileItem> { it.kind.ordinal }.thenBy { it.name.lowercase() }
    }
    val directed = if (ascending) comparator else comparator.reversed()
    return entries.sortedWith(compareByDescending<FileItem> { it.isDirectory }.then(directed))
}
