package com.anderei.yafm.data.sender

import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Small LocalSend HTTP endpoint.
 *
 * The Sender module only needs registration/info so other LocalSend clients can see us and
 * answer our announcement. Incoming uploads are explicitly rejected; this module sends files.
 */
class LocalSendServer(
    private val identity: (port: Int) -> LocalSendIdentity,
    private val onRegister: (LocalSendDevice) -> Unit,
) {
    @Volatile
    var port: Int = 0
        private set

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val clients = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "yafm-localsend-http").apply { isDaemon = true }
    }

    fun start(preferredPort: Int = LOCALSEND_DEFAULT_PORT): Int {
        var socket: ServerSocket? = null
        var selectedPort = 0
        for (candidate in preferredPort until preferredPort + 20) {
            try {
                socket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(candidate))
                }
                selectedPort = candidate
                break
            } catch (error: IOException) {
                Log.w(TAG, "bind $candidate: ${error.message}")
            }
        }
        val bound = socket ?: throw IOException("No free port for the LocalSend server")
        port = selectedPort
        serverSocket = bound
        running = true
        Log.d(TAG, "HTTP server listening on $selectedPort")
        acceptThread = Thread({ acceptLoop(bound) }, "yafm-localsend-accept").apply {
            isDaemon = true
            start()
        }
        return selectedPort
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.shutdownNow()
        runCatching { clients.awaitTermination(1, TimeUnit.SECONDS) }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            } catch (_: IOException) {
                continue
            }
            Log.d(TAG, "accepted ${client.inetAddress?.hostAddress}")
            runCatching { clients.execute { handle(client) } }
                .onFailure { Log.w(TAG, "client dispatch failed", it) }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 30_000
            val output = socket.getOutputStream()
            val request = try {
                readRequest(BufferedInputStream(socket.getInputStream()), output)
            } catch (error: Exception) {
                Log.w(TAG, "request read failed", error)
                null
            } ?: run {
                Log.w(TAG, "invalid request from ${socket.inetAddress?.hostAddress}")
                respond(output, 400, JSONObject().put("message", "Invalid request"))
                return
            }

            val path = request.path
            Log.d(TAG, "${request.method} $path from ${socket.inetAddress?.hostAddress}")
            when {
                request.method == "GET" && path == "/api/localsend/v2/info" -> {
                    respond(output, 200, identity(port).toJson())
                }

                request.method == "POST" && path == "/api/localsend/v2/register" -> {
                    val json = runCatching { JSONObject(String(request.body, Charsets.UTF_8)) }.getOrNull()
                    val remote = parseLocalSendIdentity(
                        json ?: JSONObject(),
                        fallbackPort = LOCALSEND_DEFAULT_PORT,
                        fallbackProtocol = "http",
                    )
                    if (remote != null) {
                        val host = socket.inetAddress?.hostAddress
                        if (!host.isNullOrBlank()) {
                            onRegister(LocalSendDevice(remote, host))
                        }
                    }
                    respond(output, 200, identity(port).toJson())
                }

                request.method == "POST" &&
                    path == "/api/localsend/v2/prepare-upload" -> {
                    respond(
                        output,
                        403,
                        JSONObject().put("message", "YAFM Sender does not receive files"),
                    )
                }

                request.method == "POST" &&
                    (path == "/api/localsend/v2/upload" || path == "/api/localsend/v2/cancel") -> {
                    respond(output, 403, JSONObject().put("message", "Receiving is not enabled"))
                }

                else -> respond(output, 404, JSONObject().put("message", "Not found"))
            }
        }
    }

    private fun readRequest(input: InputStream, output: OutputStream): HttpRequest? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ', limit = 3)
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val path = parts[1].substringBefore('?')

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            headers[line.substring(0, separator).trim().lowercase()] =
                line.substring(separator + 1).trim()
        }

        if (headers["expect"]?.contains("100-continue", ignoreCase = true) == true) {
            output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray())
            output.flush()
        }

        val body = when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true ->
                readChunked(input)
            else -> {
                val length = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                readExactly(input, length)
            }
        }
        if (body.size > MAX_BODY_BYTES) return null
        return HttpRequest(method, path, headers, body)
    }

    private fun readChunked(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val line = readLine(input) ?: break
            val size = line.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size <= 0) {
                readLine(input)
                break
            }
            output.write(readExactly(input, size))
            readLine(input)
            if (output.size() > MAX_BODY_BYTES) break
        }
        return output.toByteArray()
    }

    private fun readLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) return if (bytes.size() == 0) null else bytes.toString(Charsets.UTF_8)
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.write(value)
            if (bytes.size() > MAX_HEADER_BYTES) return null
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val safeLength = length.coerceIn(0, MAX_BODY_BYTES)
        val bytes = ByteArray(safeLength)
        var offset = 0
        while (offset < safeLength) {
            val read = input.read(bytes, offset, safeLength - offset)
            if (read < 0) break
            offset += read
        }
        return if (offset == safeLength) bytes else bytes.copyOf(offset)
    }

    private fun respond(output: OutputStream, status: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        runCatching {
            output.write(header.toByteArray())
            output.write(body)
            output.flush()
        }
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private companion object {
        const val TAG = "YafmLocalSend"
        const val MAX_HEADER_BYTES = 16 * 1024
        const val MAX_BODY_BYTES = 512 * 1024
    }
}
