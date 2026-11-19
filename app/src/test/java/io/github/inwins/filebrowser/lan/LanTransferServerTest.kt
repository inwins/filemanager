package io.github.inwins.filebrowser.lan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LanTransferServerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var context: Application
    private var base = ""

    @Before
    fun setUp() {
        root = temp.newFolder("share")
        context = ApplicationProvider.getApplicationContext()
        assertTrue(LanTransferServer.start(context, root))
        base = "http://127.0.0.1:${LanTransferServer.port}/${LanTransferServer.token}"
    }

    @After
    fun tearDown() {
        LanTransferServer.stop()
    }

    @Test
    fun listsUploadsAndDownloadsFiles() {
        val list = request("$base/api/list?path=", "GET")
        assertEquals(200, list.code)
        assertTrue(list.body.contains("\"entries\":[]"))

        val name = URLEncoder.encode("测试 文件.txt", "UTF-8")
        val upload = request("$base/api/upload?path=&name=$name", "PUT", "hello lan".toByteArray())
        assertEquals(200, upload.code)
        assertEquals("hello lan", File(root, "测试 文件.txt").readText())

        val listAfter = request("$base/api/list?path=", "GET")
        assertTrue(listAfter.body.contains("测试 文件.txt"))

        val download = request("$base/api/download?path=$name", "GET")
        assertEquals(200, download.code)
        assertEquals("hello lan", download.body)
    }

    @Test
    fun rejectsWrongTokenAndTraversal() {
        val wrong = request("http://127.0.0.1:${LanTransferServer.port}/wrong/api/list?path=", "GET")
        assertEquals(404, wrong.code)

        val traversal = request("$base/api/list?path=../../", "GET")
        assertEquals(403, traversal.code)

        val traversalDownload = request("$base/api/download?path=../../etc/hosts", "GET")
        assertEquals(403, traversalDownload.code)
    }

    @Test
    fun createsFoldersAndSupportsRanges() {
        val name = URLEncoder.encode("文档", "UTF-8")
        val mkdir = request("$base/api/mkdir?path=&name=$name", "POST")
        assertEquals(200, mkdir.code)
        assertTrue(File(root, "文档").isDirectory)

        File(root, "range.txt").writeText("0123456789")
        val response = request("$base/api/download?path=range.txt", "GET", range = "bytes=3-5")
        assertEquals(206, response.code)
        assertEquals("345", response.body)
        assertEquals("bytes 3-5/10", response.headers["Content-Range"])
    }


    @Test
    fun clientUploadsListsAndDownloads() {
        val peer = LanPeer(
            id = "local",
            name = "Local",
            host = "127.0.0.1",
            port = LanTransferServer.port,
            token = LanTransferServer.token,
        )
        val bytes = "app to app".toByteArray()
        var uploaded = 0L
        LanClient.upload(peer, "", "互传.txt", bytes.size.toLong(), ByteArrayInputStream(bytes)) { sent, total ->
            uploaded = sent
            assertEquals(bytes.size.toLong(), total)
        }
        assertEquals(bytes.size.toLong(), uploaded)

        val entries = LanClient.list(peer, "")
        assertEquals(1, entries.size)
        assertEquals("互传.txt", entries.first().name)

        val destination = File(temp.root, "received.txt")
        var downloaded = 0L
        LanClient.download(peer, entries.first().path, destination) { received, total ->
            downloaded = received
            assertEquals(bytes.size.toLong(), total)
        }
        assertEquals("app to app", destination.readText())
        assertEquals(bytes.size.toLong(), downloaded)
        assertFalse(File(temp.root, "received.txt.part").exists())

        LanClient.mkdir(peer, "", "client-dir")
        assertTrue(File(root, "client-dir").isDirectory)
    }


    @Test
    fun supportsEmptyFilesAndUniqueNames() {
        val empty = request("$base/api/upload?path=&name=empty.txt", "PUT", ByteArray(0))
        assertEquals(200, empty.code)
        assertEquals(0L, File(root, "empty.txt").length())

        val first = request("$base/api/upload?path=&name=dup.txt", "PUT", "one".toByteArray())
        val second = request("$base/api/upload?path=&name=dup.txt", "PUT", "two".toByteArray())
        assertEquals(200, first.code)
        assertEquals(200, second.code)
        assertEquals("one", File(root, "dup.txt").readText())
        assertEquals("two", File(root, "dup (1).txt").readText())

        assertEquals(200, request("$base/api/upload?path=&name=plain", "PUT", "p".toByteArray()).code)
        assertEquals(200, request("$base/api/upload?path=&name=plain", "PUT", "q".toByteArray()).code)
        assertTrue(File(root, "plain").isFile)
        assertTrue(File(root, "plain (1)").isFile)
    }

    @Test
    fun clientUploadsEmptyFile() {
        val peer = LanPeer(
            id = "local",
            name = "Local",
            host = "127.0.0.1",
            port = LanTransferServer.port,
            token = LanTransferServer.token,
        )
        LanClient.upload(peer, "", "empty-client.txt", 0L, ByteArrayInputStream(ByteArray(0)))
        assertTrue(File(root, "empty-client.txt").exists())
        assertEquals(0L, File(root, "empty-client.txt").length())
    }


    @Test
    fun supportsHeadAndRejectsBadRequests() {
        File(root, "head.txt").writeText("abc")
        val head = request("$base/api/download?path=head.txt", "HEAD")
        assertEquals(200, head.code)
        assertEquals("", head.body)
        assertEquals("3", head.header("Content-Length"))

        val headList = request("$base/api/list?path=", "HEAD")
        assertEquals(200, headList.code)
        assertEquals("", headList.body)
        assertTrue(headList.header("Content-Length")!!.toLong() > 0)

        val rawHead = rawRequest("HEAD /wrong HTTP/1.1\r\nHost: test\r\n\r\n")
        assertTrue(rawHead.startsWith("HTTP/1.1 404"))
        assertEquals("", rawHead.substringAfter("\r\n\r\n"))

        val rawUpload = rawRequest(
            "PUT /${LanTransferServer.token}/api/upload?path=&name=no-length.txt HTTP/1.1\r\n" +
                "Host: test\r\n\r\n"
        )
        assertTrue(rawUpload.startsWith("HTTP/1.1 411"))
        assertFalse(File(root, "no-length.txt").exists())

        val rawNegative = rawRequest(
            "PUT /${LanTransferServer.token}/api/upload?path=&name=negative.txt HTTP/1.1\r\n" +
                "Host: test\r\nContent-Length: -1\r\n\r\n"
        )
        assertTrue(rawNegative.startsWith("HTTP/1.1 411"))
        assertFalse(File(root, "negative.txt").exists())

        assertEquals(405, request("$base/api/list?path=", "POST").code)
        assertEquals(404, request("$base/unknown", "GET").code)
        assertEquals(204, request("$base/favicon.ico", "GET").code)
    }

    @Test
    fun validatesDownloadResponses() {
        File(root, "full.txt").writeText("0123456789")
        val full = request("$base/api/download?path=full.txt", "GET")
        assertEquals(200, full.code)
        assertNull(full.header("Content-Range"))
        assertTrue(full.header("Accept-Ranges") == "bytes")

        val invalidRange = request("$base/api/download?path=full.txt", "GET", range = "bytes=100-200")
        assertEquals(416, invalidRange.code)

        File(root, "empty.txt").writeText("")
        val emptyRange = request("$base/api/download?path=empty.txt", "GET", range = "bytes=0-1")
        assertEquals(416, emptyRange.code)
        val emptyFull = request("$base/api/download?path=empty.txt", "GET")
        assertEquals(200, emptyFull.code)
        assertEquals("", emptyFull.body)
        assertEquals("0", emptyFull.header("Content-Length"))

        val chinese = "中文.txt"
        File(root, chinese).writeText("hello")
        val encoded = URLEncoder.encode(chinese, "UTF-8")
        val download = request("$base/api/download?path=$encoded", "GET")
        assertEquals(200, download.code)
        assertTrue(download.header("Content-Disposition")!!.contains("filename*=UTF-8''"))

        val directory = request("$base/api/download?path=", "GET")
        assertEquals(404, directory.code)
        assertEquals(404, request("$base/api/list?path=full.txt", "GET").code)

        File(root, "quote\"name.txt").writeText("x")
        val quoteEncoded = URLEncoder.encode("quote\"name.txt", "UTF-8")
        val quoted = request("$base/api/download?path=$quoteEncoded", "GET")
        assertTrue(quoted.header("Content-Disposition")!!.contains("filename=\"quote_name.txt\""))
    }

    @Test
    fun listsDirectoriesFirstAndCreatesFolderOnce() {
        File(root, "zdir").mkdirs()
        File(root, "a.txt").writeText("a")
        File(root, "b.txt").writeText("b")

        val list = request("$base/api/list?path=", "GET")
        assertEquals(200, list.code)
        val entries = JSONObject(list.body).getJSONArray("entries")
        assertEquals("zdir", entries.getJSONObject(0).getString("name"))
        assertTrue(entries.getJSONObject(0).getBoolean("dir"))
        assertEquals("a.txt", entries.getJSONObject(1).getString("name"))

        val first = request("$base/api/mkdir?path=&name=zdir", "POST")
        assertEquals(409, first.code)
        val created = request("$base/api/mkdir?path=&name=newdir", "POST")
        assertEquals(200, created.code)
        assertTrue(File(root, "newdir").isDirectory)

        val badName = URLEncoder.encode("../evil", "UTF-8")
        val badUpload = request("$base/api/upload?path=&name=$badName", "PUT", "x".toByteArray())
        assertEquals(400, badUpload.code)
    }

    @Test
    fun escapesFolderNameInHtml() {
        LanTransferServer.stop()
        val escapedRoot = temp.newFolder("<share>")
        assertTrue(LanTransferServer.start(context, escapedRoot))
        base = "http://127.0.0.1:${LanTransferServer.port}/${LanTransferServer.token}"

        val index = request("$base/", "GET")
        assertEquals(200, index.code)
        assertTrue(index.body.contains("&lt;share&gt;"))
        assertFalse(index.body.contains("<share>"))
        assertTrue(index.body.contains(LanTransferServer.token))
    }

    @Test
    fun canRestartAfterStop() {
        LanTransferServer.stop()
        assertFalse(LanTransferServer.running)
        assertTrue(LanTransferServer.start(context, root))
        base = "http://127.0.0.1:${LanTransferServer.port}/${LanTransferServer.token}"
        assertEquals(200, request("$base/api/list?path=", "GET").code)
    }

    @Test
    fun clientReportsServerErrorsWithoutLeavingFiles() {
        val peer = LanPeer(
            id = "local",
            name = "Local",
            host = "127.0.0.1",
            port = LanTransferServer.port,
            token = LanTransferServer.token,
        )
        File(root, "existing.txt").writeText("old")
        assertThrows(IOException::class.java) {
            LanClient.download(peer, "existing.txt", File(root, "existing.txt"))
        }
        assertEquals("old", File(root, "existing.txt").readText())
        assertFalse(File(root, "existing.txt.part").exists())

        assertThrows(IOException::class.java) {
            LanClient.upload(peer, "", "../evil.txt", 1L, ByteArrayInputStream(byteArrayOf(1)))
        }
        assertFalse(File(root, "evil.txt").exists())

        assertThrows(IOException::class.java) {
            LanClient.list(peer, "../../")
        }

        File(root, "folder").mkdirs()
        assertThrows(IOException::class.java) {
            LanClient.mkdir(peer, "", "folder")
        }

        assertThrows(IOException::class.java) {
            LanClient.download(peer, "missing.txt", File(root, "missing.txt"))
        }
        assertFalse(File(root, "missing.txt").exists())
        assertFalse(File(root, "missing.txt.part").exists())
    }


    @Test
    fun concurrentUploadsDoNotOverwrite() {
        val threads = (1..8).map { index ->
            Thread {
                request("$base/api/upload?path=&name=race.txt", "PUT", "data-$index".toByteArray())
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }

        val files = root.listFiles().orEmpty().filter { it.name.startsWith("race") }
        assertEquals(8, files.size)
        assertEquals(8, files.map { it.readText() }.toSet().size)
    }


    @Test
    fun handlesSpacesAndBackslashesInNames() {
        val name = "a b\\c.txt"
        val encoded = URLEncoder.encode(name, "UTF-8")
        val upload = request("$base/api/upload?path=&name=$encoded", "PUT", "content".toByteArray())
        assertEquals(200, upload.code)
        assertTrue(File(root, name).isFile)

        val list = request("$base/api/list?path=", "GET")
        assertTrue(list.body.contains("a b\\\\c.txt"))

        val download = request("$base/api/download?path=$encoded", "GET")
        assertEquals(200, download.code)
        assertEquals("content", download.body)
    }


    @Test
    fun defaultShareFolderUsesLanTransferName() {
        assertEquals("LanTransfer", LanTransferServer.defaultShareDir(context).name)
    }


    @Test
    fun handlesEdgeCaseRequests() {
        File(root, "file.txt").writeText("x")

        assertEquals(403, request("$base/api/upload?path=../../&name=x.txt", "PUT", "x".toByteArray()).code)
        assertEquals(403, request("$base/api/mkdir?path=../../&name=x", "POST").code)
        assertEquals(404, request("$base/api/upload?path=file.txt&name=x.txt", "PUT", "x".toByteArray()).code)
        assertEquals(404, request("$base/api/mkdir?path=file.txt&name=x", "POST").code)
        assertEquals(400, request("$base/api/mkdir?path=&name=../x", "POST").code)

        assertEquals(200, request("$base/api/list?flag", "GET").code)
        assertEquals(200, request("$base/api/list?path=&&", "GET").code)
        assertEquals(404, request("$base/api/list?path=%ZZ", "GET").code)
        assertEquals(400, request("$base/api/upload?path=&name=", "PUT", "x".toByteArray()).code)
        assertEquals(400, request("$base/api/mkdir?path=&name=", "POST").code)
        assertEquals(405, request("$base/unknown", "PUT").code)
        assertEquals(405, request("$base/unknown", "POST").code)

        File(root, "unknown.xyz").writeText("abc")
        val unknownMime = request("$base/api/download?path=unknown.xyz", "GET")
        assertEquals(200, unknownMime.code)
        assertEquals("application/octet-stream", unknownMime.header("Content-Type"))

        val token = LanTransferServer.token
        val longHeader = "X-Long: " + "a".repeat(9_000) + "\r\n"
        val longResponse = rawRequest("GET /$token/api/list?path= HTTP/1.1\r\nHost: test\r\n$longHeader\r\n")
        assertTrue(longResponse.startsWith("HTTP/1.1 200"))

        assertTrue(rawRequest("BAD\r\n\r\n").isEmpty())
        assertTrue(rawRequest("\r\n\r\n").isEmpty())
        assertTrue(rawRequest("GET / HTTP/1.1\r\nHost: test\r\n\r\n").startsWith("HTTP/1.1 404"))

        val noColonHeader = rawRequest(
            "GET /$token/api/list?path= HTTP/1.1\r\nHost: test\r\nBadHeader\r\n\r\n"
        )
        assertTrue(noColonHeader.startsWith("HTTP/1.1 200"))

        val otherExpect = rawRequest(
            "GET /$token/api/list?path= HTTP/1.1\r\nHost: test\r\nExpect: something\r\n\r\n"
        )
        assertTrue(otherExpect.startsWith("HTTP/1.1 200"))

        Socket("127.0.0.1", LanTransferServer.port).use { socket ->
            socket.getOutputStream().flush()
        }
        Thread.sleep(100)

        val expect = rawRequest(
            "PUT /$token/api/upload?path=&name=expect.txt HTTP/1.1\r\n" +
                "Host: test\r\nExpect: 100-continue\r\nContent-Length: 1\r\n\r\nx"
        )
        assertTrue(expect.contains("100 Continue"))
        assertTrue(expect.contains("200 OK"))
        assertTrue(File(root, "expect.txt").isFile)

        val partialResponse = partialUpload(
            "PUT /$token/api/upload?path=&name=partial.txt HTTP/1.1\r\n" +
                "Host: test\r\nContent-Length: 10\r\n\r\nabc"
        )
        assertTrue(partialResponse.startsWith("HTTP/1.1 500"))
        assertFalse(File(root, "partial.txt").exists())
    }


    @Test
    fun statusTextCoversAllCodes() {
        val expected = mapOf(
            200 to "OK",
            204 to "No Content",
            206 to "Partial Content",
            400 to "Bad Request",
            403 to "Forbidden",
            404 to "Not Found",
            405 to "Method Not Allowed",
            409 to "Conflict",
            411 to "Length Required",
            416 to "Range Not Satisfiable",
            500 to "Internal Server Error",
            503 to "Service Unavailable",
        )
        expected.forEach { (code, text) -> assertEquals(text, LanTransferServer.statusText(code)) }
        assertEquals("Error", LanTransferServer.statusText(999))
    }

    @Test
    fun stopClosesServer() {
        LanTransferServer.stop()
        assertFalse(LanTransferServer.running)
        assertEquals(0, LanTransferServer.port)
    }

    private data class Response(
        val code: Int,
        val body: String,
        val headers: Map<String, String>,
    )

    private fun Response.header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun rawRequest(text: String): String {
        Socket("127.0.0.1", LanTransferServer.port).use { socket ->
            socket.soTimeout = 5000
            socket.getOutputStream().apply {
                write(text.toByteArray(Charsets.ISO_8859_1))
                flush()
            }
            return socket.getInputStream().bufferedReader(Charsets.ISO_8859_1).readText()
        }
    }

    private fun partialUpload(text: String): String {
        Socket("127.0.0.1", LanTransferServer.port).use { socket ->
            socket.soTimeout = 5000
            socket.getOutputStream().apply {
                write(text.toByteArray(Charsets.ISO_8859_1))
                flush()
            }
            socket.shutdownOutput()
            return socket.getInputStream().bufferedReader(Charsets.ISO_8859_1).readText()
        }
    }

    private fun request(url: String, method: String, body: ByteArray? = null, range: String? = null): Response {
        val connection = URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        if (range != null) connection.setRequestProperty("Range", range)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val headers = connection.headerFields
            .filterKeys { it != null }
            .mapKeys { it.key!! }
            .mapValues { it.value.joinToString(",") }
        connection.disconnect()
        return Response(code, text, headers)
    }
}
