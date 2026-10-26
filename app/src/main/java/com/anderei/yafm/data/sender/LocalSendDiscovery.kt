package com.anderei.yafm.data.sender

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LocalSend UDP multicast discovery (224.0.0.167:53317).
 *
 * We announce ourselves and also listen for other LocalSend clients' announcements. A peer
 * receiving our announcement replies with an HTTP register to [LocalSendServer], which adds it
 * to the device list as well.
 */
class LocalSendDiscovery(
    context: Context,
    private val identity: () -> LocalSendIdentity,
    private val onDevice: (LocalSendDevice) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private var socket: MulticastSocket? = null
    private var thread: Thread? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        try {
            val wifi = appContext.getSystemService(WifiManager::class.java)
            multicastLock = runCatching {
                wifi?.createMulticastLock("yafm-localsend")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.getOrNull()

            val group = InetAddress.getByName(LOCALSEND_MULTICAST_ADDRESS)
            val multicast = try {
                MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(LOCALSEND_DEFAULT_PORT))
                    timeToLive = 4
                    soTimeout = 1_000
                }
            } catch (_: IOException) {
                // Another LocalSend instance (e.g. the official Android app) owns the
                // multicast port. We can still announce from an ephemeral UDP socket and
                // learn about peers through HTTP register/scan.
                MulticastSocket().apply { timeToLive = 4 }
            }
            val canReceive = multicast.localPort == LOCALSEND_DEFAULT_PORT
            if (canReceive) {
                var joined = false
                val interfaces = mutableListOf<NetworkInterface>()
                val enumeration = NetworkInterface.getNetworkInterfaces()
                while (enumeration?.hasMoreElements() == true) {
                    interfaces += enumeration.nextElement()
                }
                interfaces
                    .filter { it.isUp && it.supportsMulticast() && !it.isLoopback }
                    .forEach { network ->
                        runCatching {
                            multicast.joinGroup(
                                InetSocketAddress(group, LOCALSEND_DEFAULT_PORT),
                                network,
                            )
                            joined = true
                        }
                    }
                if (!joined) {
                    runCatching {
                        multicast.joinGroup(group)
                        joined = true
                    }
                }
                if (!joined) {
                    runCatching { multicast.close() }
                    throw IOException("No multicast-capable network interface")
                }
            }
            socket = multicast
            if (canReceive) {
                thread = Thread({ receiveLoop(multicast) }, "yafm-localsend-discovery").apply {
                    isDaemon = true
                    start()
                }
            }
            announce()
        } catch (error: Exception) {
            running.set(false)
            release()
            onError(error.message ?: "Couldn't start LocalSend discovery")
        }
    }

    fun announce() {
        val multicast = socket ?: return
        runCatching {
            val group = InetAddress.getByName(LOCALSEND_MULTICAST_ADDRESS)
            val payload = identity().copy(protocol = "http").toJson(announce = true)
                .toString()
                .toByteArray(Charsets.UTF_8)
            multicast.send(
                DatagramPacket(
                    payload,
                    payload.size,
                    InetSocketAddress(group, LOCALSEND_DEFAULT_PORT),
                ),
            )
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            release()
            return
        }
        release()
    }

    private fun receiveLoop(multicast: MulticastSocket) {
        val buffer = ByteArray(64 * 1024)
        while (running.get()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                multicast.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: SocketException) {
                break
            } catch (_: IOException) {
                continue
            }
            val host = packet.address?.hostAddress ?: continue
            val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
            val json = runCatching { JSONObject(text) }.getOrNull() ?: continue
            val remote = parseLocalSendIdentity(json, LOCALSEND_DEFAULT_PORT, "http") ?: continue
            if (remote.fingerprint == identity().fingerprint) continue
            onDevice(LocalSendDevice(remote, host))
        }
    }

    private fun release() {
        runCatching { socket?.close() }
        socket = null
        thread = null
        runCatching { multicastLock?.release() }
        multicastLock = null
    }
}
