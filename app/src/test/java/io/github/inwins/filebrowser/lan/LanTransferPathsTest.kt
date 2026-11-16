package io.github.inwins.filebrowser.lan

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanTransferPathsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun resolvesRootAndChild() {
        val root = temp.newFolder("share")
        val child = File(root, "sub/file.txt").apply {
            parentFile?.mkdirs()
            writeText("ok")
        }

        assertEquals(root.canonicalFile, LanTransferPaths.resolve(root, ""))
        assertEquals(child.canonicalFile, LanTransferPaths.resolve(root, "sub/file.txt"))
        assertEquals(child.canonicalFile, LanTransferPaths.resolve(root, "sub/../sub/file.txt"))
        assertEquals("sub/file.txt", LanTransferPaths.relative(root, child))
        assertEquals("", LanTransferPaths.relative(root, root))
    }

    @Test
    fun rejectsTraversalAndInvalidNames() {
        val root = temp.newFolder("share")
        assertNull(LanTransferPaths.resolve(root, "../outside"))
        assertNull(LanTransferPaths.resolve(root, "sub/../../outside"))
        assertNull(LanTransferPaths.resolve(root, "bad\u0000name"))
    }

    @Test
    fun keepsNestedRelativePaths() {
        val root = temp.newFolder("share")
        val nested = File(root, "a/b/c").apply { mkdirs() }
        assertTrue(nested.isDirectory)
        assertEquals("a/b/c", LanTransferPaths.relative(root, nested))
    }

    @Test
    fun sanitizesDownloadNames() {
        assertEquals("evil.mp3", LanTransferPaths.safeFileName("../../evil.mp3"))
        assertEquals("b.txt", LanTransferPaths.safeFileName("a/b.txt"))
        assertEquals("a\\b.txt", LanTransferPaths.safeFileName("a\\b.txt"))
        assertEquals("file", LanTransferPaths.safeFileName(".."))
        assertEquals("file", LanTransferPaths.safeFileName(""))
    }

    @Test
    fun preservesSpacesInRelativePaths() {
        val root = temp.newFolder("share")
        val spaced = File(root, " spaced ").apply { mkdirs() }
        val file = File(spaced, " file.txt").apply { writeText("ok") }
        assertEquals(file.canonicalFile, LanTransferPaths.resolve(root, " spaced / file.txt"))
    }

    @Test
    fun supportsBackslashesInAndroidFileNames() {
        val root = temp.newFolder("share")
        val file = File(root, "a\\b.txt").apply { writeText("ok") }
        assertEquals(file.canonicalFile, LanTransferPaths.resolve(root, "a\\b.txt"))
    }
}
