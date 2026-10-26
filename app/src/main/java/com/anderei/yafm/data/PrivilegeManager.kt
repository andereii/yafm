package com.anderei.yafm.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

data class PrivilegeState(
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermission: Boolean = false,
    val shizukuUid: Int? = null,
    val shizukuVersion: Int? = null,
    val rootSuFound: Boolean = false,
    val rootGranted: Boolean = false,
    val rootChecking: Boolean = false,
) {
    val shizukuAsRoot: Boolean get() = shizukuUid == 0
}

sealed interface PrivilegeEvent {
    data object ShizukuPermissionGranted : PrivilegeEvent
    data object ShizukuPermissionDenied : PrivilegeEvent
    data class Message(val text: String) : PrivilegeEvent
}

/**
 * Owns Shizuku/Root detection, permission requests and backend creation.
 *
 * Shizuku is observed live: when the server starts or stops the state updates automatically.
 * Root is only requested when the user explicitly asks for it, because su shows a system prompt.
 */
class PrivilegeManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    val shizukuExecutor = ShizukuShellExecutor()
    val rootExecutor = RootShellExecutor()

    private val _state = MutableStateFlow(PrivilegeState())
    val state: StateFlow<PrivilegeState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PrivilegeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PrivilegeEvent> = _events.asSharedFlow()

    private var listenersRegistered = false

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        scope.launch { refreshShizuku() }
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        _state.update {
            it.copy(
                shizukuRunning = false,
                shizukuPermission = false,
                shizukuUid = null,
            )
        }
    }

    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                scope.launch {
                    refreshShizuku()
                    _events.emit(
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            PrivilegeEvent.ShizukuPermissionGranted
                        } else {
                            PrivilegeEvent.ShizukuPermissionDenied
                        },
                    )
                }
            }
        }

    init {
        registerShizukuListeners()
        scope.launch {
            refreshShizuku()
            refreshRootAvailability()
        }
    }

    private fun registerShizukuListeners() {
        if (listenersRegistered) return
        listenersRegistered = runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceived, mainHandler)
            Shizuku.addBinderDeadListener(binderDead, mainHandler)
            Shizuku.addRequestPermissionResultListener(permissionResult, mainHandler)
            true
        }.getOrDefault(false)
    }

    suspend fun refreshShizuku() {
        val installed = isPackageInstalled(SHIZUKU_PACKAGE)
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permission = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = if (running && permission) {
            runCatching { Shizuku.getUid() }.getOrNull()
        } else {
            null
        }
        val version = if (running) {
            runCatching { Shizuku.getVersion() }.getOrNull()?.takeIf { it >= 0 }
        } else {
            null
        }

        _state.update {
            it.copy(
                shizukuInstalled = installed,
                shizukuRunning = running,
                shizukuPermission = permission,
                shizukuUid = uid,
                shizukuVersion = version,
            )
        }
    }

    fun requestShizukuPermission() {
        if (!_state.value.shizukuRunning) {
            _events.tryEmit(PrivilegeEvent.Message("Start Shizuku first, then try again"))
            return
        }
        runCatching {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }.onFailure { error ->
            _events.tryEmit(
                PrivilegeEvent.Message(
                    error.message ?: "Couldn't request Shizuku permission",
                ),
            )
        }
    }

    suspend fun refreshRootAvailability() {
        val found = runCatching { rootExecutor.suBinaryPresent() }.getOrDefault(false)
        _state.update { it.copy(rootSuFound = it.rootSuFound || found) }
    }

    /** Runs `su -c id`; this is the point where Magisk/KernelSU/APatch shows its prompt. */
    suspend fun activateRoot(): Result<Unit> {
        _state.update { it.copy(rootChecking = true) }
        val result = runCatching {
            // Plain su first: it is universally supported and triggers the root prompt once.
            val plain = rootExecutor.runPlain("id -u", timeoutMillis = 120_000L)
            if (plain.exitCode != 0 || plain.stdoutText.trim() != "0") {
                val detail = plain.stderr.lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                throw IOException(detail ?: "Root was not granted")
            }

            // Then try the global mount namespace (Magisk/KernelSU --mount-master) so all
            // apps' data under /data/user/0 is visible, not just our own sandbox view.
            val global = rootExecutor.runMountMaster("id -u", timeoutMillis = 30_000L)
            val hasGlobal = global.exitCode == 0 && global.stdoutText.trim() == "0"
            rootExecutor.selectMountMaster(hasGlobal)

            _state.update { it.copy(rootGranted = true, rootSuFound = true) }
        }
        _state.update { it.copy(rootChecking = false) }
        return result
    }

    fun createBackend(mode: AccessMode, shizukuAsRoot: Boolean): FileBackend {
        val externalRoot = Environment.getExternalStorageDirectory()
        return when (mode) {
            AccessMode.STANDARD -> FileRepository(externalRoot)
            AccessMode.SHIZUKU -> ShellFileBackend(
                shell = shizukuExecutor,
                navigationRoot = if (shizukuAsRoot) File("/") else externalRoot,
            )

            AccessMode.ROOT -> ShellFileBackend(
                shell = rootExecutor,
                navigationRoot = File("/"),
            )
        }
    }

    fun close() {
        if (listenersRegistered) {
            runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
            runCatching { Shizuku.removeBinderDeadListener(binderDead) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResult) }
            listenersRegistered = false
        }
        scope.cancel()
    }

    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_REQUEST_CODE = 0x5A17
    }
}
