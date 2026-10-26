package com.anderei.yafm

import com.anderei.yafm.data.FileKind
import com.anderei.yafm.data.fileKind
import com.anderei.yafm.data.uniqueChild
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileNamingTest {

    @Test
    fun `unique child appends a counter when the name is taken`() {
        val directory = Files.createTempDirectory("yafm-test").toFile()
        try {
            File(directory, "photo.jpg").createNewFile()
            File(directory, "photo (1).jpg").createNewFile()

            assertEquals("photo (2).jpg", uniqueChild(directory, "photo.jpg", false).name)
            assertEquals("notes.txt", uniqueChild(directory, "notes.txt", false).name)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `unique child keeps folder names that contain dots intact`() {
        val directory = Files.createTempDirectory("yafm-test").toFile()
        try {
            File(directory, "my.folder").mkdirs()

            assertEquals("my.folder (1)", uniqueChild(directory, "my.folder", true).name)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `file kind is derived from the extension`() {
        assertEquals(FileKind.IMAGE, fileKind(File("holiday.JPG")))
        assertEquals(FileKind.VIDEO, fileKind(File("clip.mkv")))
        assertEquals(FileKind.AUDIO, fileKind(File("song.flac")))
        assertEquals(FileKind.PDF, fileKind(File("book.pdf")))
        assertEquals(FileKind.ARCHIVE, fileKind(File("bundle.zip")))
        assertEquals(FileKind.APK, fileKind(File("app.apk")))
        assertEquals(FileKind.CODE, fileKind(File("Main.kt")))
        assertEquals(FileKind.OTHER, fileKind(File("mystery.bin")))
    }
}
