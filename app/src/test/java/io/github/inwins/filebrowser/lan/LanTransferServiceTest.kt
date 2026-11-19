package io.github.inwins.filebrowser.lan

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    @Test
    fun stopsWithoutRootAndHandlesStopAction() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val controller = Robolectric.buildService(LanTransferService::class.java)
        controller.create().startCommand(0, 0)
        assertFalse(LanTransferServer.running)

        val root = temp.newFolder("share")
        val startIntent = Intent(context, LanTransferService::class.java)
            .putExtra("root_path", root.path)
        controller.withIntent(startIntent).startCommand(0, 0)
        assertTrue(LanTransferServer.running)

        val stopIntent = Intent(context, LanTransferService::class.java)
            .setAction("io.github.inwins.filebrowser.lan.STOP")
        controller.withIntent(stopIntent).startCommand(0, 0)
        controller.destroy()
        assertFalse(LanTransferServer.running)
    }

    @Test
    fun binderAndChannelReuseAreSafe() {
        val controller = Robolectric.buildService(LanTransferService::class.java)
        assertNull(controller.create().get().onBind(null))
        controller.destroy()

        val second = Robolectric.buildService(LanTransferService::class.java)
        second.create()
        second.destroy()
    }

    @Test
    fun companionStartsAndStopsServiceIntent() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val root = temp.newFolder("share")
        LanTransferService.start(context, root)
        val started = shadowOf(context).nextStartedService
        assertNotNull(started)
        assertEquals(LanTransferService::class.java.name, started.component?.className)
        assertEquals(root.path, started.getStringExtra("root_path"))

        LanTransferService.stop(context)
        val stopped = shadowOf(context).nextStoppedService
        assertNotNull(stopped)
        assertEquals(LanTransferService::class.java.name, stopped.component?.className)
    }
}
