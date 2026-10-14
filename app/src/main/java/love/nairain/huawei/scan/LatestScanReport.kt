package love.nairain.huawei.scan

import java.util.concurrent.Executor

/** 只保留最后快照，合并连续进度和端点通知，避免慢速 socket 堆积扫描报告。 */
internal class LatestScanReport(private val worker: Executor, private val send: (String) -> Unit,
    private val failure: (Throwable) -> Unit) {
    private var latest: String? = null
    private var revision = 0L
    private var queued = false

    @Synchronized fun update(raw: String) { latest = raw; revision++ }

    @Synchronized fun request() {
        revision++
        if (queued) return
        queued = true
        worker.execute { drain() }
    }

    private fun drain() {
        while (true) {
            val snapshot = synchronized(this) { latest to revision }
            try { snapshot.first?.let(send) } catch (error: Exception) { failure(error) }
            synchronized(this) {
                if (revision == snapshot.second) { queued = false; return }
            }
        }
    }
}
