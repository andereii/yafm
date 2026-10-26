package com.anderei.yafm.ui.archive

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anderei.yafm.YafmApplication
import com.anderei.yafm.data.archive.ArchiveFileInfo
import com.anderei.yafm.data.archive.ArchiveScanner
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

data class CompressedFilesUiState(
    val isScanning: Boolean = true,
    val scannedFiles: Int = 0,
    val archives: List<ArchiveFileInfo> = emptyList(),
    val error: String? = null,
)

class CompressedFilesViewModel(application: Application) : AndroidViewModel(application) {

    private val container: com.anderei.yafm.AppContainer
        get() = (getApplication<YafmApplication>()).container
    private val scanner = ArchiveScanner()
    private val _state = MutableStateFlow(CompressedFilesUiState())
    val state: StateFlow<CompressedFilesUiState> = _state.asStateFlow()
    private var scanJob: Job? = null

    fun scan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update { it.copy(isScanning = true, scannedFiles = 0, error = null) }
            try {
                val roots = container.storage.volumes()
                    .map { it.root }
                    .distinctBy { it.absolutePath }
                val archives = withContext(Dispatchers.IO) {
                    scanner.scan(roots, skipPaths = emptySet()) { count ->
                        _state.update { it.copy(scannedFiles = count) }
                    }
                }
                _state.update { it.copy(isScanning = false, archives = archives) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isScanning = false,
                        error = e.message ?: "Scan failed",
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = false) }
    }

    fun volumeRootFor(file: File): File {
        val volume = container.storage.volumeFor(file)
        return volume?.root
            ?: container.storage.internalVolume()?.root
            ?: Environment.getExternalStorageDirectory()
    }
}
