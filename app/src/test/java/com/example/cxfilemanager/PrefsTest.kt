package com.example.cxfilemanager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.cxfilemanager.data.AppSettings
import com.example.cxfilemanager.data.Bookmark
import com.example.cxfilemanager.data.Prefs
import com.example.cxfilemanager.data.ReaderSettings
import com.example.cxfilemanager.data.ReaderTheme
import com.example.cxfilemanager.data.TextBookmark
import com.example.cxfilemanager.data.RecentEntry
import com.example.cxfilemanager.data.SortField
import com.example.cxfilemanager.data.SortOrder
import com.example.cxfilemanager.data.ThemeMode
import com.example.cxfilemanager.data.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrefsTest {
    private fun prefs(): Prefs = Prefs(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun settingsRoundTrip() {
        val p = prefs()
        val settings = AppSettings(
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            viewMode = ViewMode.GRID,
            showHidden = true,
            sortField = SortField.SIZE,
            sortOrder = SortOrder.DESC,
        )
        p.saveSettings(settings)
        assertEquals(settings, p.loadSettings())
    }

    @Test
    fun bookmarksRoundTrip() {
        val p = prefs()
        val bookmarks = listOf(
            Bookmark("Download", "/storage/emulated/0/Download"),
            Bookmark("Pictures", "/storage/emulated/0/Pictures"),
        )
        p.saveBookmarks(bookmarks)
        assertEquals(bookmarks, p.loadBookmarks())
    }

    @Test
    fun recentsRoundTripAndLimit() {
        val p = prefs()
        val entries = (1..40).map {
            RecentEntry("file$it.txt", "/tmp/file$it.txt", it.toLong())
        }
        p.saveRecents(entries)
        val loaded = p.loadRecents()
        assertEquals(30, loaded.size)
        assertEquals("file1.txt", loaded.first().name)
    }

    @Test
    fun readerSettingsRoundTrip() {
        val p = prefs()
        val settings = ReaderSettings(
            fontSize = 22f,
            lineHeight = 1.9f,
            theme = ReaderTheme.SEPIA,
            ttsRate = 1.4f,
            ttsAutoNext = false,
        )
        p.saveReaderSettings(settings)
        assertEquals(settings, p.loadReaderSettings())
    }

    @Test
    fun readerBookmarksAndProgressRoundTrip() {
        val p = prefs()
        val path = "/storage/emulated/0/Download/book.txt"
        val bookmarks = listOf(
            TextBookmark("a", 1, "第一章", 120, "第一段内容", 1000L),
            TextBookmark("b", 2, "第二章", 880, "第二段内容", 2000L),
        )
        p.saveReaderBookmarks(path, bookmarks)
        assertEquals(bookmarks, p.loadReaderBookmarks(path))

        p.saveReadingProgress(path, 555)
        assertEquals(555, p.loadReadingProgress(path))

        p.saveReaderBookmarks(path, emptyList())
        assertEquals(emptyList<TextBookmark>(), p.loadReaderBookmarks(path))
    }

    @Test
    fun lastPathRoundTrip() {
        val p = prefs()
        p.saveLastPath("/storage/emulated/0/Music")
        assertEquals("/storage/emulated/0/Music", p.lastPath("/fallback"))
        assertTrue(p.lastPath("/fallback").endsWith("Music"))
    }
}
