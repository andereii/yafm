package com.anderei.yafm

import com.anderei.yafm.ui.explorer.buildCrumbs
import com.anderei.yafm.util.shellQuoted
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ExplorerUtilsTest {

    @Test
    fun `shell quoting wraps spaces and escapes single quotes`() {
        assertEquals("'hello world'", "hello world".shellQuoted())
        assertEquals("'it'\\''s ok'", "it's ok".shellQuoted())
        assertEquals("'a\"b\$c'", "a\"b\$c".shellQuoted())
    }

    @Test
    fun `crumbs inside home start with internal storage`() {
        val home = File("/storage/emulated/0")
        val crumbs = buildCrumbs(home, File(home, "Download/Holiday"), false)

        assertEquals(
            listOf("Internal storage", "Download", "Holiday"),
            crumbs.map { it.label },
        )
    }

    @Test
    fun `root crumbs keep internal storage as a labelled segment`() {
        val home = File("/storage/emulated/0")
        val crumbs = buildCrumbs(home, File(home, "Android/data"), true)

        assertEquals(
            listOf("Root", "Internal storage", "Android", "data"),
            crumbs.map { it.label },
        )
    }

    @Test
    fun `root crumbs outside home follow the filesystem path`() {
        val home = File("/storage/emulated/0")
        val crumbs = buildCrumbs(home, File("/data/adb"), true)

        assertEquals(listOf("Root", "data", "adb"), crumbs.map { it.label })
    }

    @Test
    fun `external storage crumbs use the volume label`() {
        val home = File("/storage/emulated/0")
        val volume = File("/storage/1A2B-3C4D")
        val crumbs = buildCrumbs(
            home = home,
            current = File(volume, "Download"),
            includeFilesystemRoot = false,
            root = volume,
            rootLabel = "SD card",
        )

        assertEquals(listOf("SD card", "Download"), crumbs.map { it.label })
    }
}
