package com.example.filebrowser

import com.example.filebrowser.data.ArchiveOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArchiveOpsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun createsListsAndExtractsZip() {
        val dir = temp.newFolder("src")
        val a = File(dir, "a.txt").apply { writeText("alpha") }
        val b = File(dir, "b.txt").apply { writeText("beta") }
        val zip = File(temp.root, "out.zip")
        assertTrue(ArchiveOps.createZip(listOf(a, b), zip))
        val entries = ArchiveOps.list(zip)
        assertTrue(entries.any { it.name == "a.txt" })
        assertTrue(entries.any { it.name == "b.txt" })

        val out = temp.newFolder("out")
        assertTrue(ArchiveOps.extract(zip, out))
        assertEquals("alpha", File(out, "a.txt").readText())
        assertEquals("beta", File(out, "b.txt").readText())
    }

    @Test
    fun encryptedZipRequiresPassword() {
        val dir = temp.newFolder("secret")
        val file = File(dir, "secret.txt").apply { writeText("hidden") }
        val zip = File(temp.root, "secret.zip")
        assertTrue(ArchiveOps.createZip(listOf(file), zip, password = "pass123"))

        val out = temp.newFolder("out")
        assertFalse(ArchiveOps.extract(zip, out, password = "wrong"))
        assertTrue(ArchiveOps.extract(zip, out, password = "pass123"))
        assertEquals("hidden", File(out, "secret.txt").readText())
    }
}
