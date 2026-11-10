package io.github.inwins.filebrowser.lan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
    private var base = ""

    @Before
    fun setUp() {
        root = temp.newFolder("share")
        val context = ApplicationProvider.getApplicationContext<Application>()
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
        LanClient.upload(peer, "", "互传.txt", bytes.size.toLong(), ByteArrayInputStream(bytes))

        val entries = LanClient.list(peer, "")
        assertEquals(1, entries.size)
        assertEquals("互传.txt", entries.first().name)

        val destination = temp.newFile("received.txt")
        LanClient.download(peer, entries.first().path, destination)
        assertEquals("app to app", destination.readText())
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
