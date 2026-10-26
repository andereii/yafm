package com.anderei.yafm.data.sender

import android.net.Uri
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

const val LOCALSEND_VERSION = "2.1"
const val LOCALSEND_DEFAULT_PORT = 53317
const val LOCALSEND_MULTICAST_ADDRESS = "224.0.0.167"

data class LocalSendIdentity(
    val alias: String,
    val version: String = LOCALSEND_VERSION,
    val deviceModel: String? = null,
    val deviceType: String = "mobile",
    val fingerprint: String,
    val port: Int = LOCALSEND_DEFAULT_PORT,
    val protocol: String = "http",
    val download: Boolean = false,
) {
    fun toJson(announce: Boolean? = null): JSONObject = JSONObject().apply {
        put("alias", alias)
        put("version", version)
        if (deviceModel.isNullOrBlank()) {
            put("deviceModel", JSONObject.NULL)
        } else {
            put("deviceModel", deviceModel)
        }
        put("deviceType", deviceType)
        put("fingerprint", fingerprint)
        put("port", port)
        put("protocol", protocol)
        put("download", download)
        if (announce != null) put("announce", announce)
    }
}

data class LocalSendDevice(
    val identity: LocalSendIdentity,
    val host: String,
    val lastSeen: Long = System.currentTimeMillis(),
) {
    val alias: String get() = identity.alias
    val port: Int get() = identity.port
    val protocol: String get() = identity.protocol
    val deviceModel: String? get() = identity.deviceModel
    val deviceType: String get() = identity.deviceType
    val fingerprint: String get() = identity.fingerprint

    val key: String
        get() = fingerprint.ifBlank { "$host:$port" }

    val baseUrl: String
        get() {
            val safeHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
            return "$protocol://$safeHost:$port"
        }

    val isDesktop: Boolean get() = deviceType == "desktop" || deviceType == "web" || deviceType == "headless"
    val isSecure: Boolean get() = protocol.equals("https", ignoreCase = true)
}

data class LocalSendOutgoingFile(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("fileName", name)
        put("size", size)
        put("fileType", mimeType)
        // sha256 is optional; omitting it avoids hashing large files twice.
    }
}

fun parseLocalSendIdentity(
    json: JSONObject,
    fallbackPort: Int = LOCALSEND_DEFAULT_PORT,
    fallbackProtocol: String = "http",
): LocalSendIdentity? {
    val alias = json.optString("alias").trim()
    if (alias.isEmpty()) return null
    val protocol = json.optString("protocol")
        .takeIf { it == "http" || it == "https" }
        ?: fallbackProtocol
    val port = json.optInt("port", fallbackPort).takeIf { it in 1..65535 } ?: fallbackPort
    return LocalSendIdentity(
        alias = alias,
        version = json.optString("version").ifBlank { LOCALSEND_VERSION },
        deviceModel = json.optString("deviceModel").takeIf { it.isNotBlank() && it != "null" },
        deviceType = json.optString("deviceType").ifBlank { "desktop" },
        fingerprint = json.optString("fingerprint"),
        port = port,
        protocol = protocol,
        download = json.optBoolean("download", false),
    )
}

fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

internal fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
