package io.github.inwins.filebrowser.lan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LanTransferServerLifecycleTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val context: Application = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        LanTransferServer.stop()
    }

    @Test
    fun rejectsFilesAndUncreatableRoots() {
        val file = temp.newFile("file")
        assertFalse(LanTransferServer.start(context, file))

        val parent = temp.newFile("parent")
        val child = File(parent, "child")
        assertFalse(LanTransferServer.start(context, child))
        assertFalse(child.exists())
    }


    @Test
    fun createsMissingRootDirectory() {
        val parent = temp.newFolder("parent")
        val root = File(parent, "nested")
        assertFalse(root.exists())
        assertTrue(LanTransferServer.start(context, root))
        assertTrue(root.isDirectory)
    }

    @Test
    fun fallsBackWhenCanonicalPathFails() {
        val root = temp.newFolder("share")
        val weird = object : File(root.path) {
            override fun getCanonicalFile(): File = throw IOException("canonical failure")
        }
        assertTrue(LanTransferServer.start(context, weird))
        assertTrue(LanTransferServer.running)
    }

    @Test
    fun rejectsWhenAllPortsAreBusy() {
        val sockets = mutableListOf<ServerSocket>()
        try {
            for (port in 18800..18830) {
                sockets.add(ServerSocket().apply { bind(InetSocketAddress(port)) })
            }
            assertFalse(LanTransferServer.start(context, temp.newFolder("share")))
            assertFalse(LanTransferServer.running)
        } finally {
            sockets.forEach { runCatching { it.close() } }
        }
    }

    @Test
    fun stoppedServerHasNoUrls() {
        assertTrue(LanTransferServer.urls().isEmpty())
        assertTrue(LanTransferServer.localAddresses().all { !it.contains(":") })
        LanTransferServer.stop()
        LanTransferServer.stop()
        assertFalse(LanTransferServer.running)
    }

    @Test
    fun runningServerRefreshesAddressesAndDiscovery() {
        val root = temp.newFolder("share")
        assertTrue(LanTransferServer.start(context, root))
        try {
            Thread.sleep(2_500)
            val addresses = LanTransferServer.localAddresses()
            val urls = LanTransferServer.urls()
            assertTrue(addresses.all { !it.contains(":") })
            assertTrue(urls.size == addresses.size)
            assertTrue(urls.all { it.contains(LanTransferServer.token) })
        } finally {
            LanTransferServer.stop()
        }
    }

    @Test
    fun filesystemRootHtmlUsesSlashLabel() {
        assertTrue(LanTransferServer.start(context, File("/")))
        try {
            val url = URL("http://127.0.0.1:${LanTransferServer.port}/${LanTransferServer.token}/")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            assertTrue(body.contains("共享目录：/"))
        } finally {
            LanTransferServer.stop()
        }
    }
}
