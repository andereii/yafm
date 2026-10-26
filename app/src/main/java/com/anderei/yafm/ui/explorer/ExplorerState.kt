package com.anderei.yafm.ui.explorer

import com.anderei.yafm.data.AccessMode
import com.anderei.yafm.data.FileItem
import java.io.File

enum class SortOption(val label: String) {
    NAME("Name"),
    DATE("Date"),
    SIZE("Size"),
    TYPE("Type"),
}

enum class LayoutMode {
    LIST,
    GRID,
}

enum class ClipboardMode {
    COPY,
    MOVE,
}

data class Clipboard(val mode: ClipboardMode, val paths: List<String>)

data class ExplorerState(
    val home: File,
    val root: File,
    val rootLabel: String = "Internal storage",
    val currentDir: File,
    val entries: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
    val selection: Set<String> = emptySet(),
    val sort: SortOption = SortOption.NAME,
    val ascending: Boolean = true,
    val showHidden: Boolean = false,
    val layout: LayoutMode = LayoutMode.LIST,
    val searchActive: Boolean = false,
    val query: String = "",
    val clipboard: Clipboard? = null,
    val error: String? = null,
    val accessMode: AccessMode = AccessMode.STANDARD,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermission: Boolean = false,
    val shizukuUid: Int? = null,
    val shizukuVersion: Int? = null,
    val rootSuFound: Boolean = false,
    val rootGranted: Boolean = false,
    val rootChecking: Boolean = false,
    val rootForAppDataEnabled: Boolean = false,
) {
    val isSelectionMode: Boolean get() = selection.isNotEmpty()

    val isAtRoot: Boolean get() = currentDir.absolutePath == root.absolutePath

    val canGoUp: Boolean get() = !isAtRoot && currentDir.parentFile != null

    val shizukuAsRoot: Boolean get() = shizukuUid == 0

    val currentTitle: String
        get() = when {
            isAtRoot -> rootLabel
            currentDir.absolutePath == home.absolutePath -> "Internal storage"
            currentDir.absolutePath == "/" -> "Root"
            else -> currentDir.name.ifBlank { "Storage" }
        }

    val modeLabel: String
        get() = when (accessMode) {
            AccessMode.STANDARD -> ""
            AccessMode.SHIZUKU -> if (shizukuAsRoot) "Shizuku · root" else "Shizuku"
            AccessMode.ROOT -> "Root"
        }
}
