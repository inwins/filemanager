package io.github.inwins.filebrowser.lan

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LanTransferServiceTest {
    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun tearDown() {
        LanTransferServer.stop()
    }

    @Test
    fun startsAndStopsSharing() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val root = temp.newFolder("share")
        val intent = Intent(context, LanTransferService::class.java)
            .putExtra("root_path", root.path)

        val controller = Robolectric.buildService(LanTransferService::class.java, intent)
        controller.create().startCommand(0, 0)
        assertTrue(LanTransferServer.running)
        assertTrue(LanTransferServer.port > 0)
        assertTrue(LanTransferServer.token.isNotBlank())
        assertEquals(root.canonicalFile, LanTransferServer.shareRoot())

        controller.destroy()
        assertFalse(LanTransferServer.running)
    }
}
