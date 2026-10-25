package com.example.filebrowser

import com.example.filebrowser.util.RootListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootListParserTest {
    private val listing = """
        total 500
        drwxr-xr-x  22 root   root       4096 2026-10-25 10:07 .
        drwxr-xr-x  22 root   root       4096 2026-10-25 10:07 ..
        drwx------   7 root   root       4096 2026-10-25 10:07 adb
        -rw-r--r--   1 root   root        123 2026-10-25 10:08 file with space.txt
        lrwxrwxrwx   1 root   root         11 2009-01-01 08:00 bin -> /system/bin
        -rw-r--r--   1 root   root         42 2026-10-25 10:09 .hidden
    """.trimIndent()

    @Test
    fun parsesEntriesAndSkipsDotDirectories() {
        val items = RootListParser.parse(listing, "/data", showHidden = false)
        assertEquals(3, items.size)
        val adb = items.first { it.name == "adb" }
        assertTrue(adb.isDirectory)
        assertTrue(adb.root)
        assertEquals("/data/adb", adb.path)
        assertEquals("drwx------", adb.permissions)

        val spaced = items.first { it.name == "file with space.txt" }
        assertFalse(spaced.isDirectory)
        assertEquals(123L, spaced.size)
        assertEquals("/data/file with space.txt", spaced.path)
        assertTrue(spaced.lastModified > 0)

        val link = items.first { it.name == "bin" }
        assertFalse(link.isDirectory)
        assertEquals("/data/bin", link.path)
        assertTrue(items.none { it.name == ".hidden" })
    }

    @Test
    fun hiddenEntriesAreIncludedWhenRequested() {
        val items = RootListParser.parse(listing, "/", showHidden = true)
        assertTrue(items.any { it.name == ".hidden" })
        assertTrue(items.all { it.path.startsWith("/") })
    }
}
