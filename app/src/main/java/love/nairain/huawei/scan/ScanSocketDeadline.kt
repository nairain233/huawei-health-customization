package love.nairain.huawei.scan

import android.net.LocalSocket
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** SO_TIMEOUT 加绝对截止时间，覆盖连接、慢速读写和等待确认。 */
internal object ScanSocketDeadline {
    private val timer = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "ScanSocketDeadline").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    /** 显式 shutdown 唤醒阻塞的 accept，close 负责释放描述符和抽象地址。 */
    fun closeServer(server: android.net.LocalServerSocket) {
        runCatching { android.system.Os.shutdown(server.fileDescriptor, android.system.OsConstants.SHUT_RDWR) }
        runCatching { server.close() }
    }

    fun closeSocket(socket: LocalSocket) {
        runCatching { android.system.Os.shutdown(socket.fileDescriptor, android.system.OsConstants.SHUT_RDWR) }
        runCatching { socket.close() }
    }

    fun <T> use(socket: LocalSocket, action: () -> T): T {
        socket.soTimeout = ScanBridgeProtocol.TIMEOUT_MS
        val timeout = timer.schedule({ closeSocket(socket) },
            ScanBridgeProtocol.TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        return try { action() } finally { timeout.cancel(false) }
    }
}
