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
        assertNull(LanTransferPaths.resolve(root, "..\\..\\outside"))
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
}
