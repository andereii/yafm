package com.anderei.yafm

import com.anderei.yafm.data.archive.ArchiveFormat
import com.anderei.yafm.data.archive.ArchiveRepository
import com.anderei.yafm.data.archive.CreateArchiveOptions
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

class ArchiveRepositoryTest {

    private val repository = ArchiveRepository()

    private fun tempDir(): File = Files.createTempDirectory("yafm-archive").toFile()

    @Test
    fun zipRoundTrip() = runBlocking {
        val root = tempDir()
        try {
            val source = File(root, "hello.txt").apply { writeText("hello yafm") }
            val archive = File(root, "out.zip")
            repository.create(listOf(source), archive, CreateArchiveOptions(ArchiveFormat.ZIP))
                .getOrThrow()
            assertTrue(archive.isFile)

            val output = File(root, "unzipped")
            repository.extract(archive, output).getOrThrow()
            assertEquals("hello yafm", File(output, "hello.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun tarGzRoundTripKeepsFolderTree() = runBlocking {
        val root = tempDir()
        try {
            val folder = File(root, "album").apply { mkdirs() }
            File(folder, "song.txt").writeText("la la")
            val archive = File(root, "album.tar.gz")
            repository.create(
                listOf(folder),
                archive,
                CreateArchiveOptions(ArchiveFormat.TAR_GZ, level = 6),
            ).getOrThrow()

            val output = File(root, "tar-out")
            repository.extract(archive, output).getOrThrow()
            assertEquals("la la", File(output, "album/song.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun encryptedZipNeedsPassword() = runBlocking {
        val root = tempDir()
        try {
            val source = File(root, "secret.txt").apply { writeText("classified") }
            val archive = File(root, "secret.zip")
            repository.create(
                listOf(source),
                archive,
                CreateArchiveOptions(ArchiveFormat.ZIP, password = "hunter2"),
            ).getOrThrow()

            val withoutPassword = repository.extract(archive, File(root, "nope"))
            assertTrue(withoutPassword.isFailure)

            val output = File(root, "ok")
            repository.extract(archive, output, "hunter2").getOrThrow()
            assertEquals("classified", File(output, "secret.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun tarVariantsRoundTrip() = runBlocking {
        val root = tempDir()
        try {
            val source = File(root, "note.txt").apply { writeText("variant") }
            for (format in listOf(
                ArchiveFormat.TAR,
                ArchiveFormat.TAR_GZ,
                ArchiveFormat.TAR_BZ2,
                ArchiveFormat.TAR_XZ,
            )) {
                val archive = File(root, "note${format.extension}")
                repository.create(
                    listOf(source),
                    archive,
                    CreateArchiveOptions(format, level = 5),
                ).getOrThrow()
                val output = File(root, "out-${format.name}")
                repository.extract(archive, output).getOrThrow()
                assertEquals("variant", File(output, "note.txt").readText())
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun gzipSingleStreamRoundTrip() = runBlocking {
        val root = tempDir()
        try {
            val archive = File(root, "note.txt.gz")
            FileOutputStream(archive).use { out ->
                GzipCompressorOutputStream(out).use { gzip ->
                    gzip.write("gzip payload".toByteArray())
                }
            }
            val output = File(root, "gzip-out")
            repository.extract(archive, output).getOrThrow()
            assertEquals("gzip payload", File(output, "note.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun formatDetectionSupportsAliases() {
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.fromName("photo.tgz"))
        assertEquals(ArchiveFormat.TAR_BZ2, ArchiveFormat.fromName("photo.tbz2"))
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.fromName("photo.jar"))
        assertEquals(ArchiveFormat.SEVEN_ZIP, ArchiveFormat.fromName("photo.7z"))
    }
}
