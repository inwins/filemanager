package com.example.cxfilemanager

import com.example.cxfilemanager.data.FileItem
import com.example.cxfilemanager.data.SortField
import com.example.cxfilemanager.data.SortOrder
import com.example.cxfilemanager.data.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileItemTest {
    private fun item(name: String, dir: Boolean = false, size: Long = 0, modified: Long = 0): FileItem =
        FileItem(path = "/tmp/$name", name = name, isDirectory = dir, size = size, lastModified = modified)

    @Test
    fun detectsFileKinds() {
        assertTrue(item("photo.JPG").isImage)
        assertTrue(item("movie.mp4").isVideo)
        assertTrue(item("song.flac").isAudio)
        assertTrue(item("archive.zip").isArchive)
        assertTrue(item("app.apk").isApk)
        assertTrue(item("notes.md").isText)
        assertFalse(item("folder", dir = true).isImage)
    }

    @Test
    fun sortPutsFoldersFirst() {
        val items = listOf(
            item("b.txt", size = 20),
            item("a", dir = true),
            item("c.txt", size = 10),
        )
        val sorted = com.example.cxfilemanager.data.FileOps.sort(items, SortSpec(SortField.NAME, SortOrder.ASC))
        assertEquals(listOf("a", "b.txt", "c.txt"), sorted.map { it.name })

        val bySize = com.example.cxfilemanager.data.FileOps.sort(items, SortSpec(SortField.SIZE, SortOrder.ASC))
        assertEquals("a", bySize.first().name)
        assertEquals(listOf("c.txt", "b.txt"), bySize.drop(1).map { it.name })
    }
}
