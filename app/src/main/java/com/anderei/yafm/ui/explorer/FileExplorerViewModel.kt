package com.anderei.yafm.ui.explorer

import android.annotation.SuppressLint
import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anderei.yafm.data.AccessMode
import com.anderei.yafm.data.FileBackend
import com.anderei.yafm.data.FileRepository
import com.anderei.yafm.data.OperationResult
import com.anderei.yafm.data.PrivilegeEvent
import com.anderei.yafm.data.PrivilegeManager
import com.anderei.yafm.data.PrivilegeState
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

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val home: File = Environment.getExternalStorageDirectory()
    private val privileges = PrivilegeManager(application)
    private var backend: FileBackend = FileRepository(home)

    private val stagingDirectory: File = File(
        application.getExternalFilesDir(null) ?: application.cacheDir,
        "staging",
    )

    private val _state = MutableStateFlow(
        ExplorerState(home = home, root = home, currentDir = home),
    )
    val state: StateFlow<ExplorerState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var loadJob: Job? = null
    private var pendingShizukuPermission = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            stagingDirectory.deleteRecursively()
            stagingDirectory.mkdirs()
        }
        observePrivileges()
        load(home)
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
                            applyAccessMode(AccessMode.SHIZUKU)
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
        if (current.accessMode != AccessMode.SHIZUKU) return

        if (!privilege.shizukuRunning) {
            applyAccessMode(AccessMode.STANDARD)
            emitMessage("Shizuku stopped, switched back to standard access")
        } else if (privilege.shizukuPermission) {
            val desiredRoot = if (privilege.shizukuAsRoot) File("/") else home
            if (current.root != desiredRoot) {
                applyAccessMode(AccessMode.SHIZUKU)
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
                applyAccessMode(AccessMode.STANDARD)
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
                applyAccessMode(AccessMode.SHIZUKU)
            }

            AccessMode.ROOT -> {
                if (_state.value.rootChecking) return
                viewModelScope.launch {
                    val result = privileges.activateRoot()
                    if (result.isSuccess) {
                        applyAccessMode(AccessMode.ROOT)
                        emitMessage("Root access granted")
                    } else {
                        emitMessage(result.exceptionOrNull()?.message ?: "Root access was denied")
                    }
                }
            }
        }
    }

    private fun applyAccessMode(mode: AccessMode) {
        val privilege = privileges.state.value
        val navigationRoot = when (mode) {
            AccessMode.STANDARD -> home
            AccessMode.SHIZUKU -> if (privilege.shizukuAsRoot) File("/") else home
            AccessMode.ROOT -> File("/")
        }

        backend = privileges.createBackend(mode, privilege.shizukuAsRoot)
        val current = _state.value.currentDir
        val target = if (isWithin(current, navigationRoot)) current else navigationRoot

        _state.update {
            it.copy(
                accessMode = mode,
                root = navigationRoot,
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

    fun navigateTo(directory: File) {
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

    fun delete(files: List<File>) {
        if (files.isEmpty()) return
        if (!ensureMutable(files)) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val result = backend.delete(files)
                val deletedPaths = files.map { it.absolutePath }
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
                emitMessage(
                    when {
                        result.failed == 0 -> "Deleted ${result.succeeded} ${noun(result.succeeded)}"
                        result.succeeded == 0 -> "Couldn't delete ${result.failed} ${noun(result.failed)}"
                        else -> "Deleted ${result.succeeded}, ${result.failed} failed"
                    },
                )
                reloadQuietly()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
                emitMessage(e.message ?: "Couldn't delete these items")
            }
        }
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
