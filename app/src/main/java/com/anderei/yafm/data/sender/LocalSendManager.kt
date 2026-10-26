package com.anderei.yafm.data.sender

import android.content.Context
import android.os.Build
import android.util.Log
import com.anderei.yafm.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Coordinates the LocalSend identity, discovery endpoint and HTTP client.
 * Only one instance should be active while the Sender screen is visible.
 */
class LocalSendManager(
    context: Context,
    private val settings: SettingsStore,
    private val onDevice: (LocalSendDevice) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val client = LocalSendClient(appContext)
    private var server: LocalSendServer? = null
    private var discovery: LocalSendDiscovery? = null
    private var serverPort: Int = LOCALSEND_DEFAULT_PORT

    val ownFingerprint: String get() = settings.senderFingerprint

    fun start(): Boolean {
        if (server != null) return true
        val alias = Build.MODEL?.takeIf { it.isNotBlank() }
            ?.let { "YAFM on $it" }
            ?: "YAFM"
        val newServer = LocalSendServer(
            identity = { port -> identity(port, alias) },
            onRegister = { device -> deliver(device) },
        )
        try {
            serverPort = newServer.start()
        } catch (error: Exception) {
            Log.e("YafmLocalSend", "server start failed", error)
            return false
        }
        server = newServer
        Log.d("YafmLocalSend", "server started on $serverPort")
        val newDiscovery = LocalSendDiscovery(
            context = appContext,
            identity = { identity(serverPort, alias) },
            onDevice = { device -> deliver(device) },
            onError = onError,
        )
        discovery = newDiscovery
        newDiscovery.start()
        return true
    }

    val isRunning: Boolean get() = server != null

    fun stop() {
        discovery?.stop()
        discovery = null
        server?.stop()
        server = null
    }

    fun refresh() {
        discovery?.announce()
    }

    suspend fun probe(host: String, port: Int): LocalSendDevice? =
        client.probe(host, port)

    /**
     * HTTP fallback discovery (LocalSend protocol section 3.2). This works even when the
     * multicast port is occupied by another LocalSend instance on the same device.
     */
    suspend fun scanLocalNetwork(port: Int = LOCALSEND_DEFAULT_PORT): List<LocalSendDevice> =
        withContext(Dispatchers.IO) {
            val hosts = localIpv4Hosts()
            if (hosts.isEmpty()) return@withContext emptyList()
            val semaphore = Semaphore(32)
            coroutineScope {
                hosts.map { host ->
                    async {
                        semaphore.withPermit {
                            client.probe(
                                host = host,
                                port = port,
                                connectTimeoutMillis = SCAN_CONNECT_TIMEOUT,
                                readTimeoutMillis = SCAN_READ_TIMEOUT,
                            )
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }

    private fun localIpv4Hosts(): List<String> {
        val addresses = mutableListOf<Inet4Address>()
        runCatching {
            val enumeration = NetworkInterface.getNetworkInterfaces()
            while (enumeration?.hasMoreElements() == true) {
                val network = enumeration.nextElement()
                if (!network.isUp || network.isLoopback) continue
                val name = network.name.lowercase()
                if (name.startsWith("rmnet") || name.startsWith("ccmni") ||
                    name.startsWith("tun") || name.startsWith("ppp") ||
                    name.startsWith("dummy")
                ) {
                    continue
                }
                network.interfaceAddresses.forEach { interfaceAddress ->
                    val address = interfaceAddress.address
                    if (address is Inet4Address &&
                        !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress
                    ) {
                        addresses += address
                    }
                }
            }
        }
        val hosts = linkedSetOf<String>()
        addresses.distinctBy { it.hostAddress }.forEach { address ->
            val octets = address.hostAddress?.split('.') ?: return@forEach
            if (octets.size != 4) return@forEach
            val prefix = octets.take(3).joinToString(".")
            for (last in 1..254) {
                val host = "$prefix.$last"
                if (host != address.hostAddress) hosts += host
            }
        }
        return hosts.toList()
    }

    suspend fun send(
        device: LocalSendDevice,
        files: List<LocalSendOutgoingFile>,
        pin: String?,
        onProgress: (sent: Long, total: Long, file: LocalSendOutgoingFile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val identity = identity(serverPort, ownAlias())
        val prepared = client.prepareUpload(device, identity, files, pin)
        when (prepared) {
            PrepareUploadResult.NoTransferNeeded -> return@withContext
            is PrepareUploadResult.Ready -> {
                val accepted = files.filter { it.id in prepared.tokens }
                var sessionCancelled = false
                try {
                    accepted.forEachIndexed { index, file ->
                        val token = prepared.tokens[file.id] ?: return@forEachIndexed
                        client.upload(device, prepared.sessionId, file, token) { sent, total ->
                            onProgress(sent, total, file)
                        }
                    }
                } catch (error: Exception) {
                    sessionCancelled = true
                    runCatching { client.cancel(device, prepared.sessionId) }
                    throw error
                } finally {
                    if (!sessionCancelled) {
                        // The protocol allows the receiver to clean up after the last upload.
                    }
                }
            }
        }
    }

    private fun ownAlias(): String =
        Build.MODEL?.takeIf { it.isNotBlank() }?.let { "YAFM on $it" } ?: "YAFM"

    private fun identity(port: Int, alias: String): LocalSendIdentity = LocalSendIdentity(
        alias = alias,
        version = LOCALSEND_VERSION,
        deviceModel = Build.MODEL,
        deviceType = "mobile",
        fingerprint = settings.senderFingerprint,
        port = port,
        protocol = "http",
        download = false,
    )

    private fun deliver(device: LocalSendDevice) {
        if (device.fingerprint == settings.senderFingerprint) return
        onDevice(device)
    }

    private companion object {
        const val SCAN_CONNECT_TIMEOUT = 350
        const val SCAN_READ_TIMEOUT = 700
    }
}
