package com.anderei.yafm.data

import android.system.Os
import java.io.File

enum class FileKind {
    FOLDER,
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    DOCUMENT,
    SPREADSHEET,
    ARCHIVE,
    APK,
    CODE,
    TEXT,
    OTHER,
}

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val kind: FileKind,
    val ownerUid: Int? = null,
) {
    val path: String get() = file.absolutePath

    companion object {
        fun from(file: File): FileItem {
            val isDirectory = file.isDirectory
            return from(
                file = file,
                isDirectory = isDirectory,
                size = if (isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                ownerUid = if (isDirectory) {
                    runCatching { Os.lstat(file.absolutePath).st_uid }.getOrNull()
                } else {
                    null
                },
            )
        }

        fun from(
            file: File,
            isDirectory: Boolean,
            size: Long,
            lastModified: Long,
            ownerUid: Int? = null,
        ): FileItem = FileItem(
            file = file,
            name = file.name,
            isDirectory = isDirectory,
            size = if (isDirectory) 0L else size,
            lastModified = lastModified,
            kind = fileKind(file.name, isDirectory),
            ownerUid = ownerUid,
        )
    }
}

private val imageExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "tif", "tiff",
)
private val videoExtensions = setOf(
    "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "flv",
)
private val audioExtensions = setOf(
    "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "amr", "mid", "m4b",
)
private val documentExtensions = setOf(
    "doc", "docx", "odt", "rtf", "epub", "pages", "tex",
)
private val spreadsheetExtensions = setOf(
    "xls", "xlsx", "csv", "ods", "numbers",
)
private val archiveExtensions = setOf(
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "iso", "jar",
)
private val codeExtensions = setOf(
    "kt", "kts", "java", "xml", "json", "yaml", "yml", "html", "css", "js", "ts", "tsx",
    "jsx", "py", "rb", "rs", "go", "c", "cpp", "h", "hpp", "sh", "toml", "gradle", "properties",
)
private val textExtensions = setOf(
    "txt", "md", "log", "ini", "conf", "cfg", "srt", "vtt", "nfo",
)

fun fileKind(file: File): FileKind = fileKind(file.name, file.isDirectory)

fun fileKind(name: String, isDirectory: Boolean): FileKind {
    if (isDirectory) return FileKind.FOLDER
    val extension = name.substringAfterLast('.', "").lowercase()
    return when (extension) {
        in imageExtensions -> FileKind.IMAGE
        in videoExtensions -> FileKind.VIDEO
        in audioExtensions -> FileKind.AUDIO
        in documentExtensions -> FileKind.DOCUMENT
        in spreadsheetExtensions -> FileKind.SPREADSHEET
        in archiveExtensions -> FileKind.ARCHIVE
        in codeExtensions -> FileKind.CODE
        in textExtensions -> FileKind.TEXT
        "pdf" -> FileKind.PDF
        "apk", "apks", "xapk", "aab" -> FileKind.APK
        else -> FileKind.OTHER
    }
}
