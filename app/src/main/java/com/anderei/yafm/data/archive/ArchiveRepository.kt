package com.anderei.yafm.data.archive

import com.github.junrar.Junrar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

data class CreateArchiveOptions(
    val format: ArchiveFormat,
    val level: Int = 5,
    val password: String? = null,
)

/** Streaming archive engine used by the Compressed Files module and the explorer. */
class ArchiveRepository {

    suspend fun create(
        sources: List<File>,
        destination: File,
        options: CreateArchiveOptions,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(sources.isNotEmpty()) { "Nothing to compress" }
            require(options.format.canCreate) { "This format can only be extracted" }
            if (destination.exists()) throw IOException("An archive with that name already exists")
            destination.parentFile?.mkdirs()

            when (options.format) {
                ArchiveFormat.ZIP -> createZip(sources, destination, options)
                ArchiveFormat.TAR,
                ArchiveFormat.TAR_GZ,
                ArchiveFormat.TAR_BZ2,
                ArchiveFormat.TAR_XZ,
                -> createTar(sources, destination, options)
                else -> throw IOException("This format can only be extracted")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { destination.delete() }
            Result.failure(e)
        }
    }

    suspend fun extract(
        archive: File,
        destination: File,
        password: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!archive.isFile) throw IOException("Archive not found")
            val format = ArchiveFormat.fromName(archive.name)
                ?: throw IOException("Unsupported archive format")
            destination.mkdirs()
            if (!destination.isDirectory) throw IOException("Can’t create the destination folder")

            when (format) {
                ArchiveFormat.ZIP -> extractZip(archive, destination, password)
                ArchiveFormat.TAR,
                ArchiveFormat.TAR_GZ,
                ArchiveFormat.TAR_BZ2,
                ArchiveFormat.TAR_XZ,
                -> extractTar(archive, destination, format)
                ArchiveFormat.SEVEN_ZIP -> extractSevenZip(archive, destination, password)
                ArchiveFormat.RAR -> extractRar(archive, destination, password)
                ArchiveFormat.GZIP,
                ArchiveFormat.BZIP2,
                ArchiveFormat.XZ,
                -> extractSingleStream(archive, destination, format)
                else -> throw IOException("Unsupported archive format")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------ ZIP

    private suspend fun createZip(
        sources: List<File>,
        destination: File,
        options: CreateArchiveOptions,
    ) {
        val password = options.password?.takeIf { it.isNotEmpty() }
        val zip = if (password != null) {
            ZipFile(destination, password.toCharArray())
        } else {
            ZipFile(destination)
        }
        zip.setBufferSize(BUFFER_SIZE)
        val parameters = ZipParameters().apply {
            compressionMethod = if (options.level <= 0) CompressionMethod.STORE else CompressionMethod.DEFLATE
            compressionLevel = zipCompressionLevel(options.level)
            isIncludeRootFolder = true
            if (password != null) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }
        sources.forEach { source ->
            currentCoroutineContext().ensureActive()
            if (!source.exists()) throw IOException("Source not found: ${source.name}")
            if (source.isDirectory) {
                zip.addFolder(source, parameters)
            } else {
                zip.addFile(source, parameters)
            }
        }
    }

    private suspend fun extractZip(archive: File, destination: File, password: String?) {
        val zip = if (!password.isNullOrEmpty()) {
            ZipFile(archive, password.toCharArray())
        } else {
            ZipFile(archive)
        }
        zip.setBufferSize(BUFFER_SIZE)
        if (zip.isEncrypted && password.isNullOrEmpty()) {
            throw IOException("This archive needs a password")
        }
        // Validate every entry before writing; zip4j also protects against traversal, but
        // validating here keeps the behavior consistent with TAR and 7z extraction.
        val destinationRoot = destination.canonicalFile
        zip.fileHeaders.forEach { header ->
            header.fileName?.takeIf { it.isNotBlank() }?.let { name ->
                safeTarget(destinationRoot, name)
            }
        }
        zip.extractAll(destination.absolutePath)
        currentCoroutineContext().ensureActive()
    }

    private fun zipCompressionLevel(level: Int): CompressionLevel = when (level.coerceIn(0, 9)) {
        0 -> CompressionLevel.NORMAL // method is STORE for level 0
        1 -> CompressionLevel.FASTEST
        2 -> CompressionLevel.FASTER
        3 -> CompressionLevel.FAST
        4 -> CompressionLevel.MEDIUM_FAST
        5 -> CompressionLevel.NORMAL
        6 -> CompressionLevel.HIGHER
        7 -> CompressionLevel.MAXIMUM
        8 -> CompressionLevel.PRE_ULTRA
        else -> CompressionLevel.ULTRA
    }

    // ------------------------------------------------------------------------ TAR

    private suspend fun createTar(
        sources: List<File>,
        destination: File,
        options: CreateArchiveOptions,
    ) {
        val fileOut = BufferedOutputStream(FileOutputStream(destination), BUFFER_SIZE)
        val compressor: OutputStream = when (options.format) {
            ArchiveFormat.TAR -> fileOut
            ArchiveFormat.TAR_GZ -> GzipCompressorOutputStream(
                fileOut,
                GzipParameters().apply { compressionLevel = options.level.coerceIn(0, 9) },
            )
            ArchiveFormat.TAR_BZ2 -> BZip2CompressorOutputStream(
                fileOut,
                options.level.coerceIn(1, 9),
            )
            ArchiveFormat.TAR_XZ -> XZCompressorOutputStream(
                fileOut,
                options.level.coerceIn(0, 9),
            )
            else -> throw IOException("Unsupported archive format")
        }

        TarArchiveOutputStream(compressor).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            sources.forEach { source ->
                if (!source.exists()) throw IOException("Source not found: ${source.name}")
                addTarEntry(tar, source, source.name)
            }
            tar.finish()
        }
    }

    private suspend fun addTarEntry(tar: TarArchiveOutputStream, file: File, entryName: String) {
        currentCoroutineContext().ensureActive()
        if (file.isDirectory) {
            val directoryName = entryName.trimEnd('/') + "/"
            tar.putArchiveEntry(TarArchiveEntry(file, directoryName))
            tar.closeArchiveEntry()
            file.listFiles()?.forEach { child ->
                addTarEntry(tar, child, "$directoryName${child.name}")
            }
        } else {
            tar.putArchiveEntry(TarArchiveEntry(file, entryName))
            file.inputStream().buffered(BUFFER_SIZE).use { input ->
                input.copyTo(tar, BUFFER_SIZE)
            }
            tar.closeArchiveEntry()
        }
    }

    private suspend fun extractTar(archive: File, destination: File, format: ArchiveFormat) {
        val buffered = BufferedInputStream(FileInputStream(archive), BUFFER_SIZE)
        val input: InputStream = when (format) {
            ArchiveFormat.TAR_GZ -> GzipCompressorInputStream(buffered)
            ArchiveFormat.TAR_BZ2 -> BZip2CompressorInputStream(buffered)
            ArchiveFormat.TAR_XZ -> XZCompressorInputStream(buffered)
            else -> buffered
        }
        TarArchiveInputStream(input).use { tar ->
            val destinationRoot = destination.canonicalFile
            while (true) {
                val entry = tar.nextEntry ?: break
                currentCoroutineContext().ensureActive()
                if (entry.isSymbolicLink || entry.isLink) {
                    continue
                }
                val target = safeTarget(destinationRoot, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).buffered(BUFFER_SIZE).use { output ->
                        tar.copyTo(output, BUFFER_SIZE)
                    }
                    entry.lastModifiedDate?.time?.let { target.setLastModified(it) }
                }
            }
        }
    }

    // -------------------------------------------------------------------- single stream

    private suspend fun extractSingleStream(archive: File, destination: File, format: ArchiveFormat) {
        val buffered = BufferedInputStream(FileInputStream(archive), BUFFER_SIZE)
        val input: InputStream = when (format) {
            ArchiveFormat.GZIP -> GzipCompressorInputStream(buffered)
            ArchiveFormat.BZIP2 -> BZip2CompressorInputStream(buffered)
            ArchiveFormat.XZ -> XZCompressorInputStream(buffered)
            else -> throw IOException("Unsupported archive format")
        }
        val name = when (format) {
            ArchiveFormat.GZIP -> archive.name.removeSuffixIgnoreCase(".gzip").removeSuffixIgnoreCase(".gz")
            ArchiveFormat.BZIP2 -> archive.name.removeSuffixIgnoreCase(".bz2")
            ArchiveFormat.XZ -> archive.name.removeSuffixIgnoreCase(".xz")
            else -> archive.name
        }.ifBlank { "extracted" }
        val target = safeTarget(destination.canonicalFile, File(name).name)
        target.parentFile?.mkdirs()
        input.use { source ->
            BufferedOutputStream(FileOutputStream(target), BUFFER_SIZE).use { output ->
                source.copyTo(output, BUFFER_SIZE)
            }
        }
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

    // -------------------------------------------------------------------- 7Z / RAR

    private suspend fun extractSevenZip(archive: File, destination: File, password: String?) {
        val builder = SevenZFile.builder().setFile(archive)
        if (!password.isNullOrEmpty()) builder.setPassword(password)
        builder.get().use { seven ->
            val destinationRoot = destination.canonicalFile
            var entry = seven.nextEntry
            val buffer = ByteArray(BUFFER_SIZE)
            while (entry != null) {
                currentCoroutineContext().ensureActive()
                val target = safeTarget(destinationRoot, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else if (entry.hasStream()) {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).buffered(BUFFER_SIZE).use { output ->
                        while (true) {
                            val read = seven.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                    entry.lastModifiedDate?.time?.let { target.setLastModified(it) }
                }
                entry = seven.nextEntry
            }
        }
    }

    private fun extractRar(archive: File, destination: File, password: String?) {
        if (password.isNullOrEmpty()) {
            Junrar.extract(archive, destination)
        } else {
            Junrar.extract(archive, destination, password)
        }
    }

    // ------------------------------------------------------------------------ helpers

    private fun safeTarget(destinationRoot: File, entryName: String): File {
        if (entryName.isBlank()) throw IOException("Invalid archive entry")
        val target = File(destinationRoot, entryName).canonicalFile
        if (target != destinationRoot && !target.toPath().startsWith(destinationRoot.toPath())) {
            throw IOException("Archive entry points outside the destination")
        }
        return target
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
