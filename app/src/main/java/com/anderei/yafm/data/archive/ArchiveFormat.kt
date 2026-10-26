package com.anderei.yafm.data.archive

import java.io.File
import java.util.Locale

enum class ArchiveFormat(
    val extension: String,
    val displayName: String,
    val canCreate: Boolean,
    val canExtract: Boolean,
    val supportsPassword: Boolean,
) {
    ZIP(".zip", "ZIP", true, true, true),
    TAR(".tar", "TAR", true, true, false),
    TAR_GZ(".tar.gz", "TAR.GZ", true, true, false),
    TAR_BZ2(".tar.bz2", "TAR.BZ2", true, true, false),
    TAR_XZ(".tar.xz", "TAR.XZ", true, true, false),
    SEVEN_ZIP(".7z", "7Z", false, true, false),
    RAR(".rar", "RAR", false, true, false),
    GZIP(".gz", "GZ", false, true, false),
    BZIP2(".bz2", "BZ2", false, true, false),
    XZ(".xz", "XZ", false, true, false),
    ;

    companion object {
        val creationFormats: List<ArchiveFormat> = entries.filter { it.canCreate }

        fun fromName(name: String): ArchiveFormat? {
            val lower = name.lowercase(Locale.ROOT)
            return when {
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> TAR_GZ
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") || lower.endsWith(".tbz") -> TAR_BZ2
                lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> TAR_XZ
                lower.endsWith(".tar") -> TAR
                lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".cbz") -> ZIP
                lower.endsWith(".7z") -> SEVEN_ZIP
                lower.endsWith(".rar") -> RAR
                lower.endsWith(".gz") || lower.endsWith(".gzip") -> GZIP
                lower.endsWith(".bz2") -> BZIP2
                lower.endsWith(".xz") -> XZ
                else -> null
            }
        }

        fun isArchiveName(name: String): Boolean = fromName(name) != null
    }
}

fun File.archiveDisplayPath(): String {
    val raw = absolutePath
    return if (raw.startsWith("/mnt/media_rw/")) {
        "/storage/" + raw.removePrefix("/mnt/media_rw/")
    } else {
        raw
    }
}
