package com.anderei.yafm.ui.explorer

import android.annotation.SuppressLint
import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anderei.yafm.AppContainer
import com.anderei.yafm.YafmApplication
import com.anderei.yafm.data.AccessMode
import com.anderei.yafm.data.FileBackend
import com.anderei.yafm.data.FileItem
import com.anderei.yafm.data.FileRepository
import com.anderei.yafm.data.OperationResult
import com.anderei.yafm.data.PrivilegeEvent
import com.anderei.yafm.data.PrivilegeManager
import com.anderei.yafm.data.PrivilegeState
import com.anderei.yafm.data.TrashEntry
import com.anderei.yafm.ui.cleaner.CleanerCategoryType
import com.anderei.yafm.ui.cleaner.CleanerItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val home: File = Environment.getExternalStorageDirectory()
    private val privileges = PrivilegeManager(application)
    private val container: AppContainer get() = (getApplication<YafmApplication>()).container

    val bookmarks = container.bookmarks.entries
    val trashRetentionDays = container.settings.trashRetentionDays
    private var backend: FileBackend = FileRepository(home)

    private val stagingDirectory: File = File(
        application.getExternalFilesDir(null) ?: application.cacheDir,
        "staging",
    )

    private val _state = MutableStateFlow(
        ExplorerState(home = home, root = home, currentDir = home),
    )
    val state: StateFlow<ExplorerState> = _state.asStateFlow()
    val privilegeState: StateFlow<PrivilegeState> = privileges.state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var loadJob: Job? = null
    private var pendingShizukuPermission = false
    private var pendingRootTarget: File? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            stagingDirectory.deleteRecursively()
            stagingDirectory.mkdirs()
        }
        observePrivileges()
        load(home)
        viewModelScope.launch {
            container.settings.useRootForAppData.collect { enabled ->
                _state.update { it.copy(rootForAppDataEnabled = enabled) }
            }
        }
        restorePersistedRootAccess()
    }

    // ------------------------------------------------------------- privileged access

    private fun observePrivileges() {
        viewModelScope.launch {
            privileges.state.collect { privilege ->
                _state.update {
                    it.copy(
                        shizukuInstalled = privilege.shizukuInstalled,
                        shizukuRunning = privilege.shizukuRunning,
                        shizukuPermission = privilege.shizukuPermission,
                        shizukuUid = privilege.shizukuUid,
                        shizukuVersion = privilege.shizukuVersion,
                        rootSuFound = privilege.rootSuFound,
                        rootGranted = privilege.rootGranted,
                        rootChecking = privilege.rootChecking,
                    )
                }
                handlePrivilegeChange(privilege)
            }
        }

        viewModelScope.launch {
            privileges.events.collect { event ->
                when (event) {
                    PrivilegeEvent.ShizukuPermissionGranted -> {
                        if (pendingShizukuPermission) {
                            pendingShizukuPermission = false
                            applyAccessMode(AccessMode.SHIZUKU, persist = true)
                        } else if (container.settings.accessMode.value == AccessMode.SHIZUKU &&
                            _state.value.accessMode == AccessMode.STANDARD
                        ) {
                            applyAccessMode(AccessMode.SHIZUKU, persist = false)
                        }
                    }

                    PrivilegeEvent.ShizukuPermissionDenied -> {
                        if (pendingShizukuPermission) {
                            pendingShizukuPermission = false
                            emitMessage("Shizuku permission was denied")
                        }
                    }

                    is PrivilegeEvent.Message -> emitMessage(event.text)
                }
            }
        }
    }

    private fun handlePrivilegeChange(privilege: PrivilegeState) {
        val current = _state.value
        val savedMode = container.settings.accessMode.value

        // Restore a persisted Shizuku preference as soon as its permission is available.
        if (current.accessMode == AccessMode.STANDARD &&
            savedMode == AccessMode.SHIZUKU &&
            privilege.shizukuRunning &&
            privilege.shizukuPermission
        ) {
            applyAccessMode(AccessMode.SHIZUKU, persist = false)
            return
        }

        if (current.accessMode != AccessMode.SHIZUKU) return

        if (!privilege.shizukuRunning) {
            applyAccessMode(AccessMode.STANDARD, persist = false)
            emitMessage("Shizuku stopped, switched back to standard access")
        } else if (privilege.shizukuPermission) {
            val desiredRoot = if (privilege.shizukuAsRoot) File("/") else home
            if (current.root != desiredRoot) {
                applyAccessMode(AccessMode.SHIZUKU, persist = false)
            }
        }
    }

    private fun restorePersistedRootAccess() {
        if (container.settings.accessMode.value != AccessMode.ROOT) return
        viewModelScope.launch {
            val result = privileges.activateRoot()
            if (container.settings.accessMode.value == AccessMode.ROOT && result.isSuccess) {
                applyAccessMode(AccessMode.ROOT, persist = false)
            }
        }
    }

    fun refreshPrivileges() {
        viewModelScope.launch {
            privileges.refreshShizuku()
            privileges.refreshRootAvailability()
        }
    }

    fun requestShizukuPermission() = privileges.requestShizukuPermission()

    fun selectAccessMode(mode: AccessMode) {
        when (mode) {
            AccessMode.STANDARD -> {
                pendingShizukuPermission = false
                pendingRootTarget = null
                applyAccessMode(AccessMode.STANDARD, persist = true)
            }

            AccessMode.SHIZUKU -> {
                val privilege = privileges.state.value
                if (!privilege.shizukuRunning) {
                    emitMessage("Shizuku is not running. Start it from the Shizuku app first.")
                    refreshPrivileges()
                    return
                }
                if (!privilege.shizukuPermission) {
                    pendingShizukuPermission = true
                    privileges.requestShizukuPermission()
                    return
                }
                pendingRootTarget = null
                applyAccessMode(AccessMode.SHIZUKU, persist = true)
            }

            AccessMode.ROOT -> {
                if (_state.value.rootChecking) return
                viewModelScope.launch {
                    val result = privileges.activateRoot()
                    if (result.isSuccess) {
                        applyAccessMode(AccessMode.ROOT, persist = true)
                        val target = pendingRootTarget
                        pendingRootTarget = null
                        if (target != null && isWithin(target, File("/"))) {
                            navigateTo(target)
                        }
                        emitMessage("Root access granted")
                    } else {
                        pendingRootTarget = null
                        emitMessage(result.exceptionOrNull()?.message ?: "Root access was denied")
                    }
                }
            }
        }
    }

    private fun applyAccessMode(mode: AccessMode, persist: Boolean = true) {
        val privilege = privileges.state.value
        val current = _state.value.currentDir
        val volume = container.storage.volumeFor(current)

        val navigationRoot: File
        val navigationLabel: String
        when {
            mode == AccessMode.ROOT ||
                (mode == AccessMode.SHIZUKU && privilege.shizukuAsRoot) -> {
                navigationRoot = File("/")
                navigationLabel = "Root"
            }

            volume != null && !volume.isPrimary -> {
                navigationRoot = volume.root
                navigationLabel = volume.description.ifBlank { "Removable storage" }
            }

            else -> {
                navigationRoot = home
                navigationLabel = "Internal storage"
            }
        }

        backend = privileges.createBackend(mode, privilege.shizukuAsRoot) {
            mode == AccessMode.ROOT && container.settings.correctSelinuxContext.value
        }
        if (persist) container.settings.setAccessMode(mode)
        val target = if (isWithin(current, navigationRoot)) current else navigationRoot

        _state.update {
            it.copy(
                accessMode = mode,
                root = navigationRoot,
                rootLabel = navigationLabel,
                currentDir = target,
                entries = emptyList(),
                isLoading = true,
                selection = emptySet(),
                searchActive = false,
                query = "",
                error = null,
            )
        }
        load(target)
    }

    /**
     * Makes a file openable by other apps. Root-only files are copied into the app's external
     * files directory, which both YAFM and FileProvider can read.
     */
    /** Lists only subfolders, used by the reusable directory picker. */
    suspend fun listFolders(directory: File): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            backend.list(directory)
                .filter { it.isDirectory && !it.name.startsWith(".") }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    suspend fun prepareForExternalUse(files: List<File>): Result<List<File>> =
        withContext(Dispatchers.IO) {
            runCatching {
                files.map { file ->
                    if (file.canRead()) file else backend.stage(file, stagingDirectory)
                }
            }
        }

    // ------------------------------------------------------------------ navigation

    fun refresh() = load(_state.value.currentDir)

    /** Opens an internal or removable storage volume, replacing the explorer's upper bound. */
    fun openStorage(storageRoot: File, label: String) {
        openStorageAt(storageRoot, label, storageRoot)
    }

    fun openStorageAt(storageRoot: File, label: String, target: File) {
        val privilege = privileges.state.value
        val mode = _state.value.accessMode
        val navigationRoot: File
        val navigationLabel: String
        when {
            mode == AccessMode.ROOT ||
                (mode == AccessMode.SHIZUKU && privilege.shizukuAsRoot) -> {
                navigationRoot = File("/")
                navigationLabel = "Root"
            }

            else -> {
                navigationRoot = storageRoot
                navigationLabel = label
            }
        }

        backend = privileges.createBackend(mode, privilege.shizukuAsRoot)
        val destination = if (isWithin(target, navigationRoot)) target else navigationRoot
        _state.update {
            it.copy(
                root = navigationRoot,
                rootLabel = navigationLabel,
                currentDir = destination,
                entries = emptyList(),
                isLoading = true,
                selection = emptySet(),
                searchActive = false,
                query = "",
                error = null,
            )
        }
        load(destination)
    }

    fun openInternalSearch() {
        openStorageAt(home, "Internal storage", home)
        startSearch()
    }

    /** Opens a root-only path only when the current mode can reach it. */
    fun openPrivilegedPath(target: File): Boolean {
        val state = _state.value
        val privilege = privileges.state.value
        val privileged = state.accessMode == AccessMode.ROOT ||
            (state.accessMode == AccessMode.SHIZUKU && privilege.shizukuAsRoot)
        if (!privileged) {
            emitMessage("This location requires root access")
            return false
        }
        openStorageAt(File("/"), "Root", target)
        return true
    }

    fun navigateTo(directory: File) {
        if (shouldUseRootForAppData(directory)) {
            pendingRootTarget = directory
            selectAccessMode(AccessMode.ROOT)
            return
        }
        val current = _state.value
        if (!isWithin(directory, current.root)) {
            emitMessage("That location needs root access")
            return
        }
        _state.update {
            it.copy(
                currentDir = directory,
                entries = emptyList(),
                isLoading = true,
                selection = emptySet(),
                searchActive = false,
                query = "",
                error = null,
            )
        }
        load(directory)
    }

    /**
     * The opt-in "Use for Android/data|obb" route: standard mode can't reliably reach these
     * folders, so enter root on demand when the setting is enabled.
     */
    private fun shouldUseRootForAppData(directory: File): Boolean {
        if (!container.settings.useRootForAppData.value) return false
        if (_state.value.accessMode != AccessMode.STANDARD) return false
        if (!isAndroidAppDataPath(directory)) return false
        val privilege = privileges.state.value
        return privilege.rootSuFound || privilege.rootGranted
    }

    private fun isAndroidAppDataPath(path: File): Boolean {
        val parts = path.absolutePath.trimEnd('/').split('/')
        val androidIndex = parts.indexOf("Android")
        if (androidIndex < 0) return false
        return parts.getOrNull(androidIndex + 1) in setOf("data", "obb")
    }

    fun goUp() {
        val current = _state.value
        if (current.isAtRoot) return
        val parent = current.currentDir.parentFile ?: return
        if (!isWithin(parent, current.root)) return
        navigateTo(parent)
    }

    // -------------------------------------------------------------------- listing

    private fun load(directory: File) {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val items = backend.list(directory)
                _state.update { current ->
                    if (current.currentDir == directory) {
                        current.copy(entries = items, isLoading = false, error = null)
                    } else {
                        current
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { current ->
                    if (current.currentDir == directory) {
                        current.copy(
                            entries = emptyList(),
                            isLoading = false,
                            error = e.message ?: "Couldn't open this folder",
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    private suspend fun reloadQuietly() {
        val directory = _state.value.currentDir
        try {
            val items = backend.list(directory)
            _state.update { current ->
                if (current.currentDir == directory) {
                    current.copy(entries = items, isLoading = false, error = null)
                } else {
                    current
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false) }
            emitMessage(e.message ?: "Couldn't refresh this folder")
        }
    }

    // --------------------------------------------------------------------- search

    fun startSearch() = _state.update { it.copy(searchActive = true, query = "") }

    fun closeSearch() = _state.update { it.copy(searchActive = false, query = "") }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    // -------------------------------------------------------------------- display

    fun setSort(option: SortOption) = _state.update { current ->
        if (current.sort == option) {
            current.copy(ascending = !current.ascending)
        } else {
            current.copy(sort = option, ascending = true)
        }
    }

    fun setAscending(ascending: Boolean) = _state.update { it.copy(ascending = ascending) }

    fun toggleHiddenFiles() {
        _state.update { it.copy(showHidden = !it.showHidden) }
        viewModelScope.launch { reloadQuietly() }
    }

    fun setLayout(layout: LayoutMode) = _state.update { it.copy(layout = layout) }

    // ----------------------------------------------------------------- selection

    fun toggleSelection(path: String) = _state.update { current ->
        val next = if (path in current.selection) {
            current.selection - path
        } else {
            current.selection + path
        }
        current.copy(selection = next)
    }

    fun selectAll(paths: Collection<String>) = _state.update { it.copy(selection = paths.toSet()) }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    // ----------------------------------------------------------------- clipboard

    fun copyToClipboard(files: List<File>, mode: ClipboardMode) {
        if (files.isEmpty()) return
        _state.update {
            it.copy(
                clipboard = Clipboard(mode, files.map(File::getAbsolutePath)),
                selection = emptySet(),
            )
        }
        val noun = noun(files.size)
        emitMessage(
            when (mode) {
                ClipboardMode.COPY -> "Copied ${files.size} $noun to clipboard"
                ClipboardMode.MOVE -> "Ready to move ${files.size} $noun"
            },
        )
    }

    fun paste() {
        val clipboard = _state.value.clipboard ?: return
        val target = _state.value.currentDir
        val sources = clipboard.paths.map(::File)

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val result = when (clipboard.mode) {
                    ClipboardMode.COPY -> backend.copy(sources, target)
                    ClipboardMode.MOVE -> backend.move(sources, target)
                }
                _state.update {
                    it.copy(
                        clipboard = if (clipboard.mode == ClipboardMode.COPY) {
                            it.clipboard
                        } else {
                            null
                        },
                        selection = emptySet(),
                        isLoading = false,
                    )
                }
                emitMessage(operationMessage(clipboard.mode, result))
                reloadQuietly()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
                emitMessage(e.message ?: "Couldn't paste these items")
            }
        }
    }

    fun clearClipboard() = _state.update { it.copy(clipboard = null) }

    // ------------------------------------------------------------------ mutations

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (!validateName(trimmed)) return
        val directory = _state.value.currentDir

        viewModelScope.launch {
            try {
                backend.createFolder(directory, trimmed)
                emitMessage("Created “$trimmed”")
                reloadQuietly()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitMessage(e.message ?: "Couldn't create folder")
            }
        }
    }

    fun rename(file: File, newName: String) {
        val trimmed = newName.trim()
        if (!validateName(trimmed)) return
        if (trimmed == file.name) return

        viewModelScope.launch {
            try {
                backend.rename(file, trimmed)
                _state.update { it.copy(selection = emptySet()) }
                emitMessage("Renamed to “$trimmed”")
                reloadQuietly()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitMessage(e.message ?: "Couldn't rename this item")
            }
        }
    }

    fun delete(files: List<File>, permanent: Boolean) {
        if (files.isEmpty()) return
        if (!ensureMutable(files)) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val deletedPaths = files.map { it.absolutePath }
                val message: String

                if (permanent) {
                    val result = backend.delete(files)
                    message = when {
                        result.failed == 0 ->
                            "Deleted ${result.succeeded} ${noun(result.succeeded)}"
                        result.succeeded == 0 ->
                            "Couldn't delete ${result.failed} ${noun(result.failed)}"
                        else -> "Deleted ${result.succeeded}, ${result.failed} failed"
                    }
                } else {
                    val result = container.trash.moveToTrash(files, backend)
                    message = when {
                        result.failed == 0 ->
                            "Moved ${result.moved} ${noun(result.moved)} to the Recycle Bin"
                        result.moved == 0 ->
                            "Couldn't move ${result.failed} ${noun(result.failed)} to the Recycle Bin"
                        else -> "Moved ${result.moved}, ${result.failed} failed"
                    }
                }

                files.forEach { file ->
                    container.bookmarks.remove(file.absolutePath)
                    container.recents.remove(file.absolutePath)
                }

                _state.update { current ->
                    val clipboard = current.clipboard?.let { clip ->
                        clip.copy(
                            paths = clip.paths.filter { path ->
                                deletedPaths.none { deleted ->
                                    path == deleted || path.startsWith(deleted + "/")
                                }
                            },
                        )
                    }
                    current.copy(
                        selection = emptySet(),
                        isLoading = false,
                        clipboard = clipboard?.takeIf { it.paths.isNotEmpty() },
                    )
                }
                emitMessage(message)
                reloadQuietly()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
                emitMessage(e.message ?: "Couldn't delete these items")
            }
        }
    }

    // -------------------------------------------------------------- bookmarks/recents

    fun toggleBookmark(item: FileItem) {
        val added = container.bookmarks.toggle(item.path, item.name, item.isDirectory)
        emitMessage(if (added) "Bookmarked" else "Bookmark removed")
    }

    fun recordRecent(item: FileItem) {
        container.recents.record(item.path, item.name, item.isDirectory, item.size)
    }

    fun recordRecentPath(path: String, name: String, isDirectory: Boolean, size: Long) {
        container.recents.record(path, name, isDirectory, size)
    }

    // -------------------------------------------------------------------- trash

    suspend fun restoreTrash(entry: TrashEntry): Result<Unit> =
        container.trash.restore(entry, backend)

    suspend fun deleteTrash(entry: TrashEntry): Result<Unit> =
        container.trash.deleteForever(entry)

    suspend fun emptyTrash(): Result<Unit> = container.trash.empty()

    /** Permanent delete used by the Cleaner (bypasses the Recycle Bin). */
    /** Finds ad-named files in app data when the current backend can reach it. */
    suspend fun scanAdFiles(): List<CleanerItem> {
        val root = File(home, "Android/data")
        return runCatching { backend.findAdFiles(root) }
            .getOrDefault(emptyList())
            .map { entry ->
                CleanerItem(
                    path = entry.path,
                    displayPath = entry.path,
                    name = entry.name,
                    isDirectory = false,
                    size = entry.size,
                    type = CleanerCategoryType.ADS,
                )
            }
    }

    suspend fun deleteCleanerFiles(files: List<File>): OperationResult {
        if (files.isEmpty()) return OperationResult(0, 0)
        if (!ensureMutable(files)) {
            throw IOException("Some selected items are protected")
        }
        val result = backend.delete(files)
        files.forEach { file ->
            container.bookmarks.remove(file.absolutePath)
            container.recents.remove(file.absolutePath)
        }
        return result
    }

    suspend fun cleanupTrash() {
        container.trash.cleanupExpired()
    }

    suspend fun updateTrashRetention(days: Int) {
        container.settings.setTrashRetentionDays(days)
        container.trash.updateRetention(days)
    }

    // -------------------------------------------------------------------- helpers

    private fun validateName(name: String): Boolean {
        if (name.isEmpty()) {
            emitMessage("Name can't be empty")
            return false
        }
        if (name.contains('/')) {
            emitMessage("Name can't contain “/”")
            return false
        }
        if (name == "." || name == "..") {
            emitMessage("That name isn't allowed")
            return false
        }
        return true
    }

    private fun ensureMutable(files: List<File>): Boolean {
        val blocked = files.firstOrNull { it.absolutePath in protectedPaths }
        if (blocked != null) {
            emitMessage("“${blocked.name.ifBlank { blocked.absolutePath }}” is protected")
            return false
        }
        return true
    }

    private fun operationMessage(mode: ClipboardMode, result: OperationResult): String {
        val verb = if (mode == ClipboardMode.COPY) "Copied" else "Moved"
        return when {
            result.failed == 0 -> "$verb ${result.succeeded} ${noun(result.succeeded)}"
            result.succeeded == 0 -> "$verb 0 ${noun(0)}, ${result.failed} failed"
            else -> "$verb ${result.succeeded}, ${result.failed} failed"
        }
    }

    private fun noun(count: Int): String = if (count == 1) "item" else "items"

    private fun emitMessage(message: String) {
        _messages.tryEmit(message)
    }

    override fun onCleared() {
        loadJob?.cancel()
        privileges.close()
    }

    @SuppressLint("SdCardPath")
    private companion object {
        val protectedPaths = setOf(
            "/",
            "/data",
            "/data/adb",
            "/system",
            "/system_ext",
            "/vendor",
            "/product",
            "/odm",
            "/apex",
            "/storage",
            "/storage/emulated",
            "/storage/emulated/0",
            "/sdcard",
            "/mnt",
            "/proc",
            "/sys",
            "/dev",
        )
    }
}
