package io.github.inwins.filebrowser

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.inwins.filebrowser.data.AppSettings
import io.github.inwins.filebrowser.data.Bookmark
import io.github.inwins.filebrowser.data.Prefs
import io.github.inwins.filebrowser.data.ReaderSettings
import io.github.inwins.filebrowser.data.ReaderTheme
import io.github.inwins.filebrowser.data.TextBookmark
import io.github.inwins.filebrowser.data.TrashEntry
import io.github.inwins.filebrowser.data.RecentEntry
import io.github.inwins.filebrowser.data.SortField
import io.github.inwins.filebrowser.data.SortOrder
import io.github.inwins.filebrowser.data.ThemeMode
import io.github.inwins.filebrowser.data.ViewMode
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
    fun trashRoundTrip() {
        val p = prefs()
        val entries = listOf(
            TrashEntry("a", "a.txt", "/sdcard/a.txt", "/data/trash/a/a.txt", 1000L, 12L, false),
            TrashEntry("b", "folder", "/sdcard/folder", "/data/trash/b/folder", 2000L, 0L, true),
        )
        p.saveTrash(entries)
        assertEquals(entries, p.loadTrash())
    }

    @Test
    fun lastPathRoundTrip() {
        val p = prefs()
        p.saveLastPath("/storage/emulated/0/Music")
        assertEquals("/storage/emulated/0/Music", p.lastPath("/fallback"))
        assertTrue(p.lastPath("/fallback").endsWith("Music"))
    }
}
