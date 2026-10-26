package com.anderei.yafm.ui.cleaner

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anderei.yafm.YafmApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as YafmApplication).container
    private val scanner = CleanerScanner()

    private val _state = MutableStateFlow(CleanerUiState())
    val state: StateFlow<CleanerUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun scan(adScanner: suspend () -> List<CleanerItem> = { emptyList() }) {
        scanJob?.cancel()
        _state.value = CleanerUiState(isScanning = true, scannedCount = 0)

        scanJob = viewModelScope.launch {
            try {
                val trashEntries = container.trash.entries.value
                val roots = withContext(Dispatchers.IO) {
                    container.storage.volumes().map { it.root }.distinctBy { it.absolutePath }
                }
                if (roots.isEmpty()) {
                    _state.value = CleanerUiState(isScanning = false, error = "No storage available")
                    return@launch
                }

                val downloadRoots = roots.map { File(it, "Download").absolutePath }.toSet()
                val appDataDir = File(
                    Environment.getExternalStorageDirectory(),
                    "Android/data/${getApplication<YafmApplication>().packageName}",
                )
                val skipPaths = buildSet {
                    add(container.trash.directory.absolutePath)
                    add(File(appDataDir, "files/staging").absolutePath)
                }

                val scanned = withContext(Dispatchers.IO) {
                    scanner.scan(
                        roots = roots,
                        downloadRoots = downloadRoots,
                        skipPaths = skipPaths,
                        onProgress = { count ->
                            _state.update { it.copy(scannedCount = count) }
                        },
                    )
                }
                val backendAds = runCatching { adScanner() }.getOrDefault(emptyList())

                val trash = CleanerCategory.trash(
                    count = trashEntries.size,
                    size = trashEntries.sumOf { it.size },
                )
                val categories = buildList {
                    add(trash)
                    CleanerCategoryType.entries
                        .filter { it != CleanerCategoryType.TRASH }
                        .forEach { type ->
                            val items = if (type == CleanerCategoryType.ADS) {
                                (scanned[CleanerCategoryType.ADS].orEmpty() + backendAds)
                                    .distinctBy { it.path }
                            } else {
                                scanned[type].orEmpty()
                            }
                            add(CleanerCategory.of(type, items))
                        }
                }

                _state.value = CleanerUiState(
                    isScanning = false,
                    scannedCount = scanned.values.sumOf { it.size },
                    categories = categories,
                    expanded = emptySet(),
                    selection = emptySet(),
                    trashSelected = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = CleanerUiState(
                    isScanning = false,
                    error = e.message ?: "Couldn’t scan storage",
                )
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = false) }
    }

    fun toggleCategory(type: CleanerCategoryType) {
        _state.update { current ->
            val category = current.categories.firstOrNull { it.type == type } ?: return@update current
            if (type == CleanerCategoryType.TRASH) {
                if (category.count == 0) current else current.copy(trashSelected = !current.trashSelected)
            } else {
                val paths = category.items.map { it.path }.toSet()
                if (paths.isEmpty()) return@update current
                val allSelected = paths.all { it in current.selection }
                current.copy(
                    selection = if (allSelected) {
                        current.selection - paths
                    } else {
                        current.selection + paths
                    },
                )
            }
        }
    }

    fun toggleItem(path: String) {
        _state.update { current ->
            current.copy(
                selection = if (path in current.selection) {
                    current.selection - path
                } else {
                    current.selection + path
                },
            )
        }
    }

    fun toggleExpanded(type: CleanerCategoryType) {
        _state.update { current ->
            current.copy(
                expanded = if (type in current.expanded) {
                    current.expanded - type
                } else {
                    current.expanded + type
                },
            )
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}
