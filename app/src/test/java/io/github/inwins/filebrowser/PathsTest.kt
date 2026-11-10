package io.github.inwins.filebrowser

import io.github.inwins.filebrowser.util.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PathsTest {
    private val root = File("/storage/emulated/0")

    @Test
    fun parentOfRootIsNull() {
        assertNull(Paths.parent("/"))
    }

    @Test
    fun parentAndBreadcrumbs() {
        val path = File(root, "Download").path
        assertEquals(root.path, Paths.parent(path))
        val crumbs = Paths.breadcrumbs(path)
        assertEquals("/", crumbs.first().second)
        assertEquals(path, crumbs.last().second)
        assertEquals("Download", crumbs.last().first)
    }

    @Test
    fun childDetection() {
        assertTrue(Paths.isSameOrChild("/sdcard", "/sdcard/Download"))
        assertTrue(Paths.isSameOrChild("/sdcard", "/sdcard"))
        assertFalse(Paths.isSameOrChild("/sdcard", "/sdcard2"))
        assertFalse(Paths.isSameOrChild("/sdcard", "/other"))
    }

    @Test
    fun validatesNames() {
        assertTrue(Paths.safeName("hello.txt"))
        assertTrue(Paths.safeName("中文 文件"))
        assertFalse(Paths.safeName(""))
        assertFalse(Paths.safeName("a/b"))
        assertFalse(Paths.safeName("."))
        assertFalse(Paths.safeName(".."))
    }
}
