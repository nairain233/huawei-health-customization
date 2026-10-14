package love.nairain.huawei.scan

import android.content.Context
import android.content.SharedPreferences
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Process
import android.util.Log
import io.github.libxposed.service.XposedService
import love.nairain.huawei.hook.HookInstallPolicy
import java.util.concurrent.Executors

/** 应用级单实例；仅前台扫描页持有端点，端点发布和清理串行执行。 */
internal class ScanReportReceiver(context: Context) {
    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ScanReportEndpoint").apply { isDaemon = true }
    }
    // 仅端点执行器读写，不能用旧 RemotePreferences 的缓存判断新会话归属。
    private val publication = ScanBridgePublication()
    val lifecycle = ScanBridgeLifecycle(worker, { binding, active ->
        open(binding as XposedService, active)
    }, ::diagnostic)

    private fun open(service: XposedService, active: () -> Boolean): AutoCloseable {
        val prefs = service.getRemotePreferences(ScanBridgeProtocol.GROUP)
        val endpoint = ScanBridgeProtocol.create()
        val server = LocalServerSocket(endpoint.address)
        val connection = Connection(server, endpoint, prefs, active)
        try {
            if (!active()) return connection.also { it.close() }
            publication.publish(connection, prefs, endpoint)
            if (active()) Thread({ connection.receive() }, "ScanReportReceive").apply { isDaemon = true }.start()
            return connection
        } catch (error: Exception) {
            connection.close()
            throw error
        }
    }

    private inner class Connection(
        val server: LocalServerSocket,
        val endpoint: ScanBridgeProtocol.Endpoint,
        val prefs: SharedPreferences,
        val active: () -> Boolean,
    ) : AutoCloseable {
        @Volatile private var closed = false
        private var client: LocalSocket? = null

        fun receive() {
            try {
                while (!closed && active()) {
                    val accepted = server.accept()
                    val stale = synchronized(this) {
                        if (closed || !active()) true else { client = accepted; false }
                    }
                    accepted.use { socket ->
                        if (!stale) runCatching {
                            ScanSocketDeadline.use(socket) {
                                val expected = runCatching {
                                    app.packageManager.getPackageUid(HookInstallPolicy.TARGET_PACKAGE, 0)
                                }.getOrNull()
                                require(ScanBridgeProtocol.allows(socket.peerCredentials.uid, expected, Process.myUid()))
                                val raw = ScanBridgeProtocol.read(socket.inputStream, endpoint.token)
                                check(!closed && active())
                                val stored = ScanReportStore.accept(app, raw)
                                socket.outputStream.write(if (stored) 1 else 0)
                                socket.outputStream.flush()
                            }
                        }.onFailure { if (!closed && active()) diagnostic(it) }
                    }
                    synchronized(this) { if (client === accepted) client = null }
                }
            } catch (error: Exception) {
                if (!closed && active()) diagnostic(error)
            } finally { close() }
        }

        override fun close() {
            val socket = synchronized(this) {
                if (closed) return
                closed = true
                client.also { client = null }
            }
            socket?.let(ScanSocketDeadline::closeSocket)
            ScanSocketDeadline.closeServer(server)
            worker.execute {
                runCatching { publication.clear(this, prefs, endpoint) }.onFailure(::diagnostic)
            }
        }
    }

    private fun diagnostic(error: Throwable) {
        Log.w("HuaweiTrim", "Scan report bridge unavailable: ${error.javaClass.simpleName}")
    }
}
