package com.example.cxfilemanager

import com.example.cxfilemanager.data.FileOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOpsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun copyAndMoveAndDelete() {
        val root = temp.newFolder("root")
        val source = File(root, "source").apply { mkdirs() }
        val file = File(source, "hello.txt").apply { writeText("hello world") }
        val target = File(root, "target")

        assertTrue(FileOps.copyRecursive(source, target))
        assertEquals("hello world", File(target, "hello.txt").readText())
        assertTrue(file.exists())

        val moved = File(root, "moved")
        assertTrue(FileOps.move(source, moved))
        assertFalse(source.exists())
        assertEquals("hello world", File(moved, "hello.txt").readText())

        assertTrue(FileOps.deleteRecursive(moved))
        assertFalse(moved.exists())
    }

    @Test
    fun refusesCopyIntoItself() {
        val dir = temp.newFolder("self")
        File(dir, "a.txt").writeText("a")
        assertFalse(FileOps.copyRecursive(dir, File(dir, "inner")))
    }

    @Test
    fun uniqueNames() {
        val dir = temp.newFolder("unique")
        val first = File(dir, "photo.jpg").apply { writeText("1") }
        assertEquals("photo (1).jpg", FileOps.uniqueFile(first).name)
        File(dir, "photo (1).jpg").writeText("2")
        assertEquals("photo (2).jpg", FileOps.uniqueFile(first).name)
    }

    @Test
    fun createRenameAndRejectInvalidNames() {
        val dir = temp.newFolder("ops")
        val folder = FileOps.createFolder(dir, "新文件夹")
        assertNotNull(folder)
        assertNull(FileOps.createFolder(dir, "../bad"))

        val file = FileOps.createFile(dir, "note.txt")
        assertNotNull(file)
        val renamed = FileOps.rename(file!!, "renamed.txt")
        assertNotNull(renamed)
        assertEquals("renamed.txt", renamed!!.name)
        assertNull(FileOps.rename(renamed, "missing/name.txt"))
    }

    @Test
    fun zipAndUnzipRoundTrip() {
        val dir = temp.newFolder("zip")
        val source = File(dir, "folder").apply { mkdirs() }
        File(source, "a.txt").writeText("alpha")
        File(source, "中文.txt").writeText("beta")
        File(source, "sub").mkdirs()
        File(source, "sub/b.txt").writeText("gamma")
        val zip = File(dir, "out.zip")

        assertTrue(FileOps.zip(listOf(source), zip))
        assertTrue(zip.length() > 0)

        val out = File(dir, "out")
        assertTrue(FileOps.unzip(zip, out))
        assertEquals("alpha", File(out, "folder/a.txt").readText())
        assertEquals("beta", File(out, "folder/中文.txt").readText())
        assertEquals("gamma", File(out, "folder/sub/b.txt").readText())
    }

    @Test
    fun zipSlipIsBlocked() {
        val dir = temp.newFolder("slip")
        val zip = File(dir, "evil.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { zos ->
            val entry = java.util.zip.ZipEntry("../evil.txt")
            zos.putNextEntry(entry)
            zos.write("evil".toByteArray())
            zos.closeEntry()
        }
        val out = File(dir, "out")
        assertTrue(FileOps.unzip(zip, out))
        assertFalse(File(dir, "evil.txt").exists())
    }

    @Test
    fun folderSizeAndSearch() {
        val root = temp.newFolder("scan")
        File(root, "one.txt").writeText("12345")
        val sub = File(root, "sub").apply { mkdirs() }
        File(sub, "two.txt").writeText("1234567890")
        File(sub, ".hidden").mkdirs()
        File(sub, ".hidden/secret.txt").writeText("x")

        assertEquals(16L, FileOps.folderSize(root))

        val visible = FileOps.search(root, "txt", showHidden = false)
        assertEquals(2, visible.size)
        val all = FileOps.search(root, "txt", showHidden = true)
        assertEquals(3, all.size)
    }

    @Test
    fun searchStopsAtLimit() {
        val root = temp.newFolder("limit")
        repeat(20) { File(root, "file$it.txt").writeText("x") }
        val results = FileOps.search(root, "file", showHidden = false, limit = 5)
        assertEquals(5, results.size)
    }
}
