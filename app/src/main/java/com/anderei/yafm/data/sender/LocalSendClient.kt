package com.anderei.yafm.data.sender

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

open class LocalSendException(
    val statusCode: Int,
    message: String,
    val body: String? = null,
) : IOException(message)

class LocalSendPinRequiredException : LocalSendException(401, "PIN required")

sealed interface PrepareUploadResult {
    data object NoTransferNeeded : PrepareUploadResult
    data class Ready(val sessionId: String, val tokens: Map<String, String>) : PrepareUploadResult
}

/**
 * Minimal LocalSend HTTP client. It deliberately trusts self-signed certificates for LAN HTTPS;
 * LocalSend uses its own self-signed certificates and the transfer never leaves the local network.
 */
class LocalSendClient(private val context: Context) {

    private val resolver = context.contentResolver

    suspend fun probe(
        host: String,
        port: Int,
        connectTimeoutMillis: Int = CONNECT_TIMEOUT,
        readTimeoutMillis: Int = READ_TIMEOUT,
    ): LocalSendDevice? = withContext(Dispatchers.IO) {
        for (scheme in listOf("http", "https")) {
            val device = runCatching {
                info(host, port, scheme, connectTimeoutMillis, readTimeoutMillis)
            }.getOrNull()
            if (device != null) return@withContext device
        }
        null
    }

    suspend fun register(device: LocalSendDevice, identity: LocalSendIdentity): LocalSendDevice? =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = identity.toJson()
                val connection = open("${device.baseUrl}/api/localsend/v2/register").apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                    val code = connection.responseCode
                    if (code !in 200..299) return@runCatching null
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val responseIdentity = parseLocalSendIdentity(
                        json,
                        fallbackPort = device.port,
                        fallbackProtocol = device.protocol,
                    ) ?: identity
                    device.copy(
                        identity = responseIdentity.copy(
                            port = device.port,
                            protocol = device.protocol,
                        ),
                    )
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

    suspend fun prepareUpload(
        device: LocalSendDevice,
        identity: LocalSendIdentity,
        files: List<LocalSendOutgoingFile>,
        pin: String? = null,
    ): PrepareUploadResult = withContext(Dispatchers.IO) {
        require(files.isNotEmpty()) { "No files to send" }
        val pinQuery = pin?.takeIf { it.isNotBlank() }?.let { "?pin=${it.urlEncoded()}" }.orEmpty()
        val connection = open("${device.baseUrl}/api/localsend/v2/prepare-upload$pinQuery").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val filesJson = JSONObject()
            files.forEach { file -> filesJson.put(file.id, file.toJson()) }
            val payload = JSONObject().apply {
                put("info", identity.toJson())
                put("files", filesJson)
            }
            val bytes = payload.toString().toByteArray()
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }

            val code = connection.responseCode
            val body = readBody(connection)
            when (code) {
                204 -> PrepareUploadResult.NoTransferNeeded
                200 -> parsePrepareResponse(body)
                401 -> throw LocalSendPinRequiredException()
                403 -> throw LocalSendException(403, "The receiver rejected the files", body)
                409 -> throw LocalSendException(409, "The receiver is busy", body)
                429 -> throw LocalSendException(429, "Too many requests", body)
                else -> throw LocalSendException(code, "Prepare upload failed (HTTP $code)", body)
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun upload(
        device: LocalSendDevice,
        sessionId: String,
        file: LocalSendOutgoingFile,
        token: String,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        val url = "${device.baseUrl}/api/localsend/v2/upload" +
            "?sessionId=${sessionId.urlEncoded()}" +
            "&fileId=${file.id.urlEncoded()}" +
            "&token=${token.urlEncoded()}"
        val connection = open(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Accept", "application/json")
            if (file.size > 0L) {
                setFixedLengthStreamingMode(file.size)
            } else {
                setChunkedStreamingMode(UPLOAD_BUFFER)
            }
        }
        try {
            val input = resolver.openInputStream(file.uri)
                ?: throw IOException("Couldn't read ${file.name}")
            input.use { source ->
                connection.outputStream.use { output ->
                    val buffer = ByteArray(UPLOAD_BUFFER)
                    var sent = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        sent += read
                        onProgress(sent, if (file.size > 0L) file.size else -1L)
                    }
                }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = readBody(connection)
                throw LocalSendException(code, "Upload failed (HTTP $code)", body)
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun cancel(device: LocalSendDevice, sessionId: String) = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${device.baseUrl}/api/localsend/v2/cancel" +
                "?sessionId=${sessionId.urlEncoded()}"
            val connection = open(url).apply { requestMethod = "POST" }
            try {
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }
        Unit
    }

    private fun info(
        host: String,
        port: Int,
        scheme: String,
        connectTimeoutMillis: Int = CONNECT_TIMEOUT,
        readTimeoutMillis: Int = READ_TIMEOUT,
    ): LocalSendDevice {
        val safeHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        val connection = open(
            "$scheme://$safeHost:$port/api/localsend/v2/info",
            connectTimeoutMillis,
            readTimeoutMillis,
        )
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw LocalSendException(code, "Device did not answer (HTTP $code)")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val identity = parseLocalSendIdentity(
                JSONObject(body),
                fallbackPort = port,
                fallbackProtocol = scheme,
            ) ?: throw IOException("Invalid LocalSend info")
            return LocalSendDevice(identity.copy(port = port, protocol = scheme), host)
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePrepareResponse(body: String): PrepareUploadResult {
        val json = runCatching { JSONObject(body) }.getOrElse {
            throw IOException("Invalid prepare-upload response")
        }
        val sessionId = json.optString("sessionId")
        if (sessionId.isBlank()) throw IOException("The receiver did not return a session")
        val filesJson = json.optJSONObject("files")
            ?: throw IOException("The receiver did not return file tokens")
        val tokens = mutableMapOf<String, String>()
        val keys = filesJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val token = filesJson.optString(key)
            if (token.isNotBlank()) tokens[key] = token
        }
        if (tokens.isEmpty()) throw IOException("The receiver accepted no files")
        return PrepareUploadResult.Ready(sessionId, tokens)
    }

    private fun open(
        url: String,
        connectTimeoutMillis: Int = CONNECT_TIMEOUT,
        readTimeoutMillis: Int = READ_TIMEOUT,
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", "YAFM LocalSend/1.0")
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = trustAllContext.socketFactory
            connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
        return connection
    }

    private fun readBody(connection: HttpURLConnection): String {
        val stream: InputStream? = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private companion object {
        const val CONNECT_TIMEOUT = 5_000
        const val READ_TIMEOUT = 120_000
        const val UPLOAD_BUFFER = 64 * 1024

        val trustAllContext: SSLContext by lazy {
            SSLContext.getInstance("TLS").apply {
                init(
                    null,
                    arrayOf<TrustManager>(
                        object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                        },
                    ),
                    SecureRandom(),
                )
            }
        }
    }
}
