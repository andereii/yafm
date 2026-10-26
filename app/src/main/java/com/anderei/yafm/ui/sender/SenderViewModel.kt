package com.anderei.yafm.ui.sender

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anderei.yafm.R
import com.anderei.yafm.YafmApplication
import com.anderei.yafm.data.sender.LocalSendDevice
import com.anderei.yafm.data.sender.LocalSendManager
import com.anderei.yafm.data.sender.LocalSendOutgoingFile
import com.anderei.yafm.data.sender.LocalSendPinRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlin.math.max

data class TransferProgress(
    val device: LocalSendDevice,
    val sentBytes: Long,
    val totalBytes: Long,
    val currentFile: String,
    val fileIndex: Int,
    val fileCount: Int,
) {
    val fraction: Float?
        get() = if (totalBytes > 0L) {
            (sentBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            null
        }
}

data class SenderUiState(
    val devices: List<LocalSendDevice> = emptyList(),
    val isScanning: Boolean = false,
    val isProbing: Boolean = false,
    val networkReady: Boolean = false,
    val selectedFiles: List<LocalSendOutgoingFile> = emptyList(),
    val transfer: TransferProgress? = null,
    val pinTarget: LocalSendDevice? = null,
) {
    val selectedBytes: Long get() = selectedFiles.sumOf { max(0L, it.size) }
}

class SenderViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val container = (application as YafmApplication).container
    private val resolver = application.contentResolver
    private val _state = MutableStateFlow(SenderUiState())
    val state: StateFlow<SenderUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val manager = LocalSendManager(
        context = application,
        settings = container.settings,
        onDevice = ::addDevice,
        onError = { message -> emitMessage(message) },
    )

    private var transferJob: Job? = null
    private var scanJob: Job? = null
    private var started = false

    fun start() {
        started = true
        val ready = manager.start()
        _state.update { it.copy(networkReady = ready) }
        if (ready) {
            refresh()
        } else {
            _state.update { it.copy(isScanning = false) }
        }
    }

    fun stop() {
        started = false
        transferJob?.cancel()
        transferJob = null
        scanJob?.cancel()
        scanJob = null
        manager.stop()
        _state.update { it.copy(isScanning = false, networkReady = false, transfer = null) }
    }

    fun refresh() {
        if (!started) return
        manager.refresh()
        _state.update { it.copy(isScanning = true) }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(1_200)
            if (_state.value.devices.isEmpty()) {
                manager.scanLocalNetwork().forEach(::addDevice)
            }
            delay(600)
            _state.update { it.copy(isScanning = false) }
        }
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> resolveFile(uri) }
            }
            _state.update { current ->
                val existing = current.selectedFiles
                val known = existing.mapTo(mutableSetOf()) { it.uri.toString() }
                val additions = resolved.filter { known.add(it.uri.toString()) }
                current.copy(selectedFiles = existing + additions)
            }
            if (resolved.isEmpty()) emitMessage(app.getString(R.string.sender_msg_files_unreadable))
        }
    }

    fun removeFile(file: LocalSendOutgoingFile) {
        _state.update { current ->
            current.copy(selectedFiles = current.selectedFiles.filterNot { it.uri == file.uri })
        }
    }

    fun clearFiles() {
        _state.update { it.copy(selectedFiles = emptyList()) }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        _state.update { it.copy(transfer = null) }
        emitMessage(app.getString(R.string.sender_msg_cancelled))
    }

    fun clearPinRequest() {
        _state.update { it.copy(pinTarget = null) }
    }

    fun send(device: LocalSendDevice, pin: String? = null) {
        if (transferJob?.isActive == true) return
        val files = _state.value.selectedFiles
        if (files.isEmpty()) {
            emitMessage(app.getString(R.string.sender_msg_choose_file))
            return
        }
        _state.update { it.copy(pinTarget = null) }
        val totalBytes = files.sumOf { max(0L, it.size) }
        transferJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    transfer = TransferProgress(
                        device = device,
                        sentBytes = 0L,
                        totalBytes = totalBytes,
                        currentFile = files.first().name,
                        fileIndex = 1,
                        fileCount = files.size,
                    ),
                )
            }
            try {
                var completedBytes = 0L
                manager.send(device, files, pin) { sent, fileTotal, file ->
                    val index = files.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
                    val knownBefore = files.take(index).sumOf { max(0L, it.size) }
                    val currentTotal = if (fileTotal > 0L) fileTotal else max(0L, file.size)
                    val sentOverall = knownBefore + sent
                    _state.update {
                        it.copy(
                            transfer = TransferProgress(
                                device = device,
                                sentBytes = sentOverall,
                                totalBytes = max(totalBytes, knownBefore + currentTotal),
                                currentFile = file.name,
                                fileIndex = index + 1,
                                fileCount = files.size,
                            ),
                        )
                    }
                    completedBytes = sentOverall
                }
                _state.update { it.copy(transfer = null) }
                emitMessage(app.getString(R.string.sender_msg_sent, files.size, device.alias))
            } catch (e: LocalSendPinRequiredException) {
                _state.update { it.copy(transfer = null, pinTarget = device) }
            } catch (e: CancellationException) {
                _state.update { it.copy(transfer = null) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(transfer = null) }
                emitMessage(app.getString(R.string.sender_msg_failed, e.message ?: "unknown error"))
            } finally {
                transferJob = null
            }
        }
    }

    fun addManualDevice(rawHost: String, port: Int) {
        val host = rawHost.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
        if (host.isBlank()) {
            emitMessage(app.getString(R.string.sender_msg_enter_ip))
            return
        }
        _state.update { it.copy(isProbing = true) }
        viewModelScope.launch {
            val device = manager.probe(host, port)
            _state.update { it.copy(isProbing = false) }
            if (device == null) {
                emitMessage(app.getString(R.string.sender_msg_not_found, "$host:$port"))
            } else {
                addDevice(device)
                emitMessage(app.getString(R.string.sender_msg_connected, device.alias))
            }
        }
    }

    private fun addDevice(device: LocalSendDevice) {
        if (device.fingerprint == manager.ownFingerprint) return
        _state.update { current ->
            val devices = current.devices.associateBy { it.key }.toMutableMap()
            devices[device.key] = device
            current.copy(
                devices = devices.values.sortedBy { it.alias.lowercase() },
                isScanning = false,
            )
        }
    }

    private fun resolveFile(uri: Uri): LocalSendOutgoingFile? = runCatching {
        var name: String? = null
        var size = -1L
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val displayName = name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "file"
        if (size <= 0L) {
            size = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull() ?: 0L
        }
        val mime = resolver.getType(uri)
            ?: File(displayName).extension.takeIf { it.isNotBlank() }?.let {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase())
            }
            ?: "application/octet-stream"
        LocalSendOutgoingFile(
            uri = uri,
            name = displayName,
            size = max(0L, size),
            mimeType = mime,
        )
    }.getOrNull()

    private fun emitMessage(message: String) {
        _messages.tryEmit(message)
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
