package com.anderei.yafm.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (value >= 100.0) {
        "${value.roundToInt()} ${units[index]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }
}

private val dateTimeFormat: DateFormat by lazy {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
}

fun formatDateTime(millis: Long): String = dateTimeFormat.format(Date(millis))

fun File.mimeType(): String {
    if (isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR

    val extension = extension.lowercase(Locale.ROOT)
    val fromPlatform = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    if (fromPlatform != null) return fromPlatform

    return when (extension) {
        "apk" -> "application/vnd.android.package-archive"
        "mkv" -> "video/x-matroska"
        "flac" -> "audio/flac"
        "epub" -> "application/epub+zip"
        "json" -> "application/json"
        "md" -> "text/markdown"
        "log", "ini", "conf", "cfg" -> "text/plain"
        else -> "*/*"
    }
}

fun openFile(context: Context, file: File): Result<Unit> = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, file.mimeType())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open with"))
}

fun shareFiles(context: Context, files: List<File>): Result<Unit> = runCatching {
    require(files.isNotEmpty())
    val authority = "${context.packageName}.fileprovider"
    val uris = files.map { FileProvider.getUriForFile(context, authority, it) }
    val single = files.size == 1
    val mime = if (single) files.first().mimeType() else {
        val types = files.map { it.mimeType() }.distinct()
        if (types.size == 1) types.first() else "*/*"
    }

    val intent = Intent(if (single) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = mime
        if (single) {
            putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        clipData = ClipData.newRawUri("shared files", uris.first()).apply {
            uris.drop(1).forEach { uri -> addItem(ClipData.Item(uri)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}
