package com.anderei.yafm

import com.anderei.yafm.ui.cleaner.CleanerCategoryType
import com.anderei.yafm.ui.cleaner.CleanerScanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CleanerScannerTest {

    @Test
    fun `cleaner skips root-level files and scans subfolders and app data ads`() = runBlocking {
        val root = Files.createTempDirectory("cleaner-test").toFile()
        try {
            File(root, "root-empty.txt").writeText("")
            File(root, "root.apk").writeText("x")
            File(root, "root-folder").mkdirs()

            val download = File(root, "Download").apply { mkdirs() }
            File(download, "download-empty.txt").writeText("")
            File(download, "app.apk").writeText("x")
            File(download, "empty-folder").mkdirs()

            val adDir = File(root, "Android/data/test/cache").apply { mkdirs() }
            File(adDir, "ad_banner.mp4").writeText("x")
            File(adDir, "normal.txt").writeText("x")

            val result = CleanerScanner().scan(
                roots = listOf(root),
                downloadRoots = setOf(download.absolutePath),
                skipPaths = emptySet(),
                onProgress = {},
            )

            val emptyPaths = result[CleanerCategoryType.EMPTY].orEmpty().map { it.path }
            assertTrue(emptyPaths.any { it.endsWith("Download/download-empty.txt") })
            assertTrue(emptyPaths.any { it.endsWith("Download/empty-folder") })
            assertFalse(emptyPaths.any { it.endsWith("root-empty.txt") })
            assertFalse(emptyPaths.any { it.endsWith("root-folder") })

            val installerNames = result[CleanerCategoryType.INSTALLERS].orEmpty().map { it.name }
            assertTrue(installerNames.contains("app.apk"))
            assertFalse(installerNames.contains("root.apk"))

            assertEquals(
                listOf("ad_banner.mp4"),
                result[CleanerCategoryType.ADS].orEmpty().map { it.name },
            )

            val downloadNames = result[CleanerCategoryType.DOWNLOADS].orEmpty().map { it.name }
            assertTrue(downloadNames.contains("app.apk"))
            assertTrue(downloadNames.contains("download-empty.txt"))
            assertFalse(downloadNames.contains("root.apk"))
        } finally {
            root.deleteRecursively()
        }
    }
}
