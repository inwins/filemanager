package io.github.inwins.filebrowser

import io.github.inwins.filebrowser.util.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    @Test
    fun formatsSizes() {
        assertEquals("0 B", Format.size(0))
        assertEquals("512 B", Format.size(512))
        assertEquals("1.0 KB", Format.size(1024))
        assertEquals("1.5 MB", Format.size(1024L * 1024 + 512 * 1024))
        assertEquals("2.0 GB", Format.size(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun naturalCompareHandlesNumbers() {
        assertTrue(Format.naturalCompare("file2", "file10") < 0)
        assertTrue(Format.naturalCompare("file10", "file2") > 0)
        assertTrue(Format.naturalCompare("File1", "file1") == 0)
        assertTrue(Format.naturalCompare("a", "b") < 0)
    }

    @Test
    fun relativePathWorks() {
        assertEquals("Download/a.txt", Format.relativePath("/storage/emulated/0/Download/a.txt", "/storage/emulated/0"))
        assertEquals("", Format.relativePath("/sdcard", "/sdcard"))
        assertEquals("/other", Format.relativePath("/other", "/sdcard"))
    }
}
