package io.github.inwins.filebrowser.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("file_browser_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        return AppSettings(
            themeMode = enumValue(sp.getString(KEY_THEME, null), ThemeMode.SYSTEM),
            dynamicColor = sp.getBoolean(KEY_DYNAMIC, true),
            viewMode = enumValue(sp.getString(KEY_VIEW, null), ViewMode.LIST),
            showHidden = sp.getBoolean(KEY_HIDDEN, false),
            sortField = enumValue(sp.getString(KEY_SORT_FIELD, null), SortField.NAME),
            sortOrder = enumValue(sp.getString(KEY_SORT_ORDER, null), SortOrder.ASC),
            useTrash = sp.getBoolean(KEY_USE_TRASH, true),
        )
    }

    fun saveSettings(settings: AppSettings) {
        sp.edit()
            .putString(KEY_THEME, settings.themeMode.name)
            .putBoolean(KEY_DYNAMIC, settings.dynamicColor)
            .putString(KEY_VIEW, settings.viewMode.name)
            .putBoolean(KEY_HIDDEN, settings.showHidden)
            .putString(KEY_SORT_FIELD, settings.sortField.name)
            .putString(KEY_SORT_ORDER, settings.sortOrder.name)
            .putBoolean(KEY_USE_TRASH, settings.useTrash)
            .apply()
    }

    fun loadBookmarks(): List<Bookmark> {
        val raw = sp.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val path = obj.optString("path")
                    if (path.isNotBlank()) add(Bookmark(obj.optString("name", path), path))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveBookmarks(bookmarks: List<Bookmark>) {
        val array = JSONArray()
        bookmarks.forEach {
            array.put(JSONObject().put("name", it.name).put("path", it.path))
        }
        sp.edit().putString(KEY_BOOKMARKS, array.toString()).apply()
    }

    fun loadRecents(): List<RecentEntry> {
        val raw = sp.getString(KEY_RECENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val path = obj.optString("path")
                    if (path.isNotBlank()) {
                        add(
                            RecentEntry(
                                name = obj.optString("name", path.substringAfterLast('/')),
                                path = path,
                                openedAt = obj.optLong("time", 0L),
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveRecents(recents: List<RecentEntry>) {
        val array = JSONArray()
        recents.take(MAX_RECENTS).forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("path", it.path)
                    .put("time", it.openedAt)
            )
        }
        sp.edit().putString(KEY_RECENTS, array.toString()).apply()
    }


    fun loadTrash(): List<TrashEntry> {
        val raw = sp.getString(KEY_TRASH, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isBlank()) continue
                    add(
                        TrashEntry(
                            id = id,
                            name = obj.optString("name"),
                            originalPath = obj.optString("originalPath"),
                            trashedPath = obj.optString("trashedPath"),
                            deletedAt = obj.optLong("deletedAt", 0L),
                            size = obj.optLong("size", 0L),
                            isDirectory = obj.optBoolean("isDirectory", false),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveTrash(entries: List<TrashEntry>) {
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("originalPath", it.originalPath)
                    .put("trashedPath", it.trashedPath)
                    .put("deletedAt", it.deletedAt)
                    .put("size", it.size)
                    .put("isDirectory", it.isDirectory)
            )
        }
        sp.edit().putString(KEY_TRASH, array.toString()).apply()
    }

    fun loadReaderSettings(): ReaderSettings {
        return ReaderSettings(
            fontSize = sp.getFloat(KEY_READER_FONT, 18f).coerceIn(14f, 30f),
            lineHeight = sp.getFloat(KEY_READER_LINE_HEIGHT, 1.65f).coerceIn(1.2f, 2.4f),
            theme = enumValue(sp.getString(KEY_READER_THEME, null), ReaderTheme.SYSTEM),
            ttsRate = sp.getFloat(KEY_READER_TTS_RATE, 1.0f).coerceIn(0.4f, 2.5f),
            ttsAutoNext = sp.getBoolean(KEY_READER_TTS_AUTO_NEXT, true),
        )
    }

    fun saveReaderSettings(settings: ReaderSettings) {
        sp.edit()
            .putFloat(KEY_READER_FONT, settings.fontSize)
            .putFloat(KEY_READER_LINE_HEIGHT, settings.lineHeight)
            .putString(KEY_READER_THEME, settings.theme.name)
            .putFloat(KEY_READER_TTS_RATE, settings.ttsRate)
            .putBoolean(KEY_READER_TTS_AUTO_NEXT, settings.ttsAutoNext)
            .apply()
    }

    fun loadReaderBookmarks(path: String): List<TextBookmark> {
        val array = readerBookmarkMap().optJSONArray(path) ?: return emptyList()
        return runCatching {
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        TextBookmark(
                            id = obj.optString("id"),
                            chapterIndex = obj.optInt("chapter", 0),
                            chapterTitle = obj.optString("title"),
                            offset = obj.optInt("offset", 0),
                            preview = obj.optString("preview"),
                            createdAt = obj.optLong("time", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveReaderBookmarks(path: String, bookmarks: List<TextBookmark>) {
        val map = readerBookmarkMap()
        if (bookmarks.isEmpty()) {
            map.remove(path)
        } else {
            val array = JSONArray()
            bookmarks.forEach {
                array.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("chapter", it.chapterIndex)
                        .put("title", it.chapterTitle)
                        .put("offset", it.offset)
                        .put("preview", it.preview)
                        .put("time", it.createdAt)
                )
            }
            map.put(path, array)
        }
        sp.edit().putString(KEY_READER_BOOKMARKS, map.toString()).apply()
    }

    fun loadReadingProgress(path: String): Int {
        return runCatching {
            JSONObject(sp.getString(KEY_READER_PROGRESS, "{}") ?: "{}").optInt(path, 0)
        }.getOrDefault(0)
    }

    fun saveReadingProgress(path: String, offset: Int) {
        val map = runCatching {
            JSONObject(sp.getString(KEY_READER_PROGRESS, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        map.put(path, offset)
        sp.edit().putString(KEY_READER_PROGRESS, map.toString()).apply()
    }

    private fun readerBookmarkMap(): JSONObject {
        return runCatching {
            JSONObject(sp.getString(KEY_READER_BOOKMARKS, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
    }

    fun loadRemoteProfiles(): List<RemoteProfile> {
        val raw = sp.getString(KEY_REMOTE_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isBlank()) continue
                    add(
                        RemoteProfile(
                            id = id,
                            name = obj.optString("name", id),
                            protocol = enumValue(obj.optString("protocol"), RemoteProtocol.SMB),
                            host = obj.optString("host"),
                            port = obj.optInt("port", 0),
                            username = obj.optString("username"),
                            password = SecretStore.decrypt(obj.optString("password")),
                            domain = obj.optString("domain"),
                            share = obj.optString("share"),
                            rootPath = obj.optString("rootPath", "/").ifBlank { "/" },
                            authMethod = enumValue(
                                obj.optString("authMethod"),
                                RemoteAuthMethod.PASSWORD,
                            ),
                            privateKeyPath = obj.optString("privateKeyPath"),
                            keyPassphrase = SecretStore.decrypt(obj.optString("keyPassphrase")),
                            hostKeyFingerprint = obj.optString("hostKeyFingerprint"),
                            guest = obj.optBoolean("guest", false),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveRemoteProfiles(profiles: List<RemoteProfile>) {
        val array = JSONArray()
        profiles.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("protocol", it.protocol.name)
                    .put("host", it.host)
                    .put("port", it.port)
                    .put("username", it.username)
                    .put("password", SecretStore.encrypt(it.password))
                    .put("domain", it.domain)
                    .put("share", it.share)
                    .put("rootPath", it.rootPath)
                    .put("authMethod", it.authMethod.name)
                    .put("privateKeyPath", it.privateKeyPath)
                    .put("keyPassphrase", SecretStore.encrypt(it.keyPassphrase))
                    .put("hostKeyFingerprint", it.hostKeyFingerprint)
                    .put("guest", it.guest)
            )
        }
        sp.edit().putString(KEY_REMOTE_PROFILES, array.toString()).apply()
    }

    fun loadMarkers(): Map<String, FileMarker> {
        val raw = sp.getString(KEY_MARKERS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val path = keys.next()
                    val value = obj.optJSONObject(path) ?: continue
                    val marker = FileMarker(
                        color = enumValue(value.optString("color"), MarkerColor.NONE),
                        label = value.optString("label"),
                        favorite = value.optBoolean("favorite", false),
                        pinned = value.optBoolean("pinned", false),
                    )
                    if (!marker.isEmpty) put(path, marker)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveMarkers(markers: Map<String, FileMarker>) {
        val obj = JSONObject()
        markers.forEach { (path, marker) ->
            if (!marker.isEmpty) {
                obj.put(
                    path,
                    JSONObject()
                        .put("color", marker.color.name)
                        .put("label", marker.label)
                        .put("favorite", marker.favorite)
                        .put("pinned", marker.pinned)
                )
            }
        }
        sp.edit().putString(KEY_MARKERS, obj.toString()).apply()
    }

    fun rootAccessEnabled(): Boolean = sp.getBoolean(KEY_ROOT_ACCESS, false)

    fun saveRootAccessEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_ROOT_ACCESS, enabled).apply()
    }

    fun lastPath(fallback: String): String = sp.getString(KEY_LAST_PATH, fallback) ?: fallback

    fun saveLastPath(path: String) {
        sp.edit().putString(KEY_LAST_PATH, path).apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(name: String?, fallback: T): T {
        if (name == null) return fallback
        return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC = "dynamic"
        private const val KEY_VIEW = "view"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_SORT_FIELD = "sort_field"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_RECENTS = "recents"
        private const val KEY_LAST_PATH = "last_path"
        private const val KEY_READER_FONT = "reader_font"
        private const val KEY_READER_LINE_HEIGHT = "reader_line_height"
        private const val KEY_READER_THEME = "reader_theme"
        private const val KEY_READER_TTS_RATE = "reader_tts_rate"
        private const val KEY_READER_TTS_AUTO_NEXT = "reader_tts_auto_next"
        private const val KEY_READER_BOOKMARKS = "reader_bookmarks"
        private const val KEY_READER_PROGRESS = "reader_progress"
        private const val KEY_ROOT_ACCESS = "root_access"
        private const val KEY_REMOTE_PROFILES = "remote_profiles"
        private const val KEY_MARKERS = "file_markers"
        private const val KEY_USE_TRASH = "use_trash"
        private const val KEY_TRASH = "trash_entries"
        private const val MAX_RECENTS = 30
    }
}
