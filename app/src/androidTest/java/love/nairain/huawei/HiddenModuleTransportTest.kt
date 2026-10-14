package love.nairain.huawei

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.scan.ScanBridgeProtocol
import love.nairain.huawei.scan.ScanSocketDeadline
import love.nairain.huawei.watchface.ModuleResources
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 仅验证 Android 资源与 socket 原语；不代表 HMA 或宿主真机回归通过。 */
@RunWith(AndroidJUnit4::class)
class HiddenModuleTransportTest {
    @Test fun resourcesAndFooterLoadWithoutPackageContextLookup() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(app) {
            override fun createPackageContext(packageName: String, flags: Int): Context =
                error("Package context lookup must not be used")
        }
        val resources = ModuleResources.load(context.packageManager, ApplicationInfo(app.applicationInfo))
        assertEquals(app.getString(R.string.wf_import), resources.getString(R.string.wf_import))
        assertEquals("love.nairain.huawei", resources.getResourcePackageName(R.string.wf_import))
        assertTrue(resources.assets.open("local-watchface-footer.js").bufferedReader().use { it.readText() }.isNotBlank())
        assertThrows(IllegalArgumentException::class.java) {
            ModuleResources.load(context.packageManager, ApplicationInfo(app.applicationInfo).apply { packageName = "other" })
        }
    }

    @Test fun abstractSocketAuthenticatesFramesAndReleasesEndpoint() {
        val endpoint = ScanBridgeProtocol.create()
        val server = LocalServerSocket(endpoint.address)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val receive = worker.submit<String> {
                server.accept().use { socket ->
                    ScanSocketDeadline.use(socket) {
                        assertEquals(Process.myUid(), socket.peerCredentials.uid)
                        val raw = ScanBridgeProtocol.read(socket.inputStream, endpoint.token)
                        socket.outputStream.write(1)
                        raw
                    }
                }
            }
            LocalSocket().use { socket ->
                ScanSocketDeadline.use(socket) {
                    socket.connect(LocalSocketAddress(endpoint.address, LocalSocketAddress.Namespace.ABSTRACT))
                    ScanBridgeProtocol.write(socket.outputStream, endpoint.token, "报告")
                    assertEquals(1, socket.inputStream.read())
                }
            }
            assertEquals("报告", receive.get(5, TimeUnit.SECONDS))
        } finally { server.close(); worker.shutdownNow() }
        LocalServerSocket(endpoint.address).close()
    }

    @Test fun closingListenerWakesAcceptAndAllowsNewSession() {
        val endpoint = ScanBridgeProtocol.create()
        val server = LocalServerSocket(endpoint.address)
        val worker = Executors.newSingleThreadExecutor()
        val accepting = CountDownLatch(1)
        try {
            val blocked = worker.submit<Boolean> {
                accepting.countDown()
                runCatching { server.accept().close() }.isFailure
            }
            assertTrue(accepting.await(2, TimeUnit.SECONDS))
            ScanSocketDeadline.closeServer(server)
            assertTrue(blocked.get(3, TimeUnit.SECONDS))
            LocalServerSocket(endpoint.address).close()
        } finally { ScanSocketDeadline.closeServer(server); worker.shutdownNow() }
    }

    @Test fun silentPeerCannotKeepReadAliveBeyondDeadline() {
        val endpoint = ScanBridgeProtocol.create()
        val server = LocalServerSocket(endpoint.address)
        val worker = Executors.newSingleThreadExecutor()
        val release = CountDownLatch(1)
        try {
            val peer = worker.submit {
                server.accept().use { release.await(5, TimeUnit.SECONDS) }
            }
            LocalSocket().use { socket ->
                val started = System.nanoTime()
                assertThrows(Exception::class.java) {
                    ScanSocketDeadline.use(socket) {
                        socket.connect(LocalSocketAddress(endpoint.address, LocalSocketAddress.Namespace.ABSTRACT))
                        ScanBridgeProtocol.read(socket.inputStream, endpoint.token)
                    }
                }
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5000)
            }
            release.countDown()
            peer.get(5, TimeUnit.SECONDS)
        } finally { release.countDown(); server.close(); worker.shutdownNow() }
    }
}
