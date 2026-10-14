package love.nairain.huawei.scan

import android.content.SharedPreferences
import android.net.LocalSocket
import android.net.LocalSocketAddress
import love.nairain.huawei.hook.util.ModuleLogger
import java.util.concurrent.Executors

/** 进程级订阅只持有应用数据；宿主只读通信组，不写远程配置。 */
internal class ScanReportSender(
    private val preferences: () -> SharedPreferences,
    private val moduleUid: Int,
    private val logger: ModuleLogger,
) {
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ScanReportSend").apply { isDaemon = true }
    }
    private var prefs: SharedPreferences? = null
    private val latest = LatestScanReport(worker, ::send) { diagnostic(it) }
    // SharedPreferences 可能仅弱引用监听器，必须在宿主进程存活期间保留它。
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == ScanBridgeProtocol.ENDPOINT) latest.request()
    }

    init { worker.execute { runCatching { source() }.onFailure(::diagnostic) } }

    private fun source(): SharedPreferences = prefs ?: preferences().also {
        it.registerOnSharedPreferenceChangeListener(listener)
        prefs = it
    }

    fun update(raw: String) = latest.update(raw)
    fun retry() = latest.request()

    private fun send(raw: String) {
        val endpoint = ScanBridgeProtocol.endpoint(source().getString(ScanBridgeProtocol.ENDPOINT, null)) ?: return
        LocalSocket().use { socket ->
            ScanSocketDeadline.use(socket) {
                socket.connect(LocalSocketAddress(endpoint.address, LocalSocketAddress.Namespace.ABSTRACT))
                // 接收方也须属于框架告知的模块 UID，避免将报告发给冒占端点的进程。
                require(socket.peerCredentials.uid == moduleUid)
                ScanBridgeProtocol.write(socket.outputStream, endpoint.token, raw)
                require(socket.inputStream.read() in 0..1)
            }
        }
    }

    private fun diagnostic(error: Throwable) {
        logger.warn("Scan report socket deferred: ${error.javaClass.simpleName}")
    }
}
