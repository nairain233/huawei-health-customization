package love.nairain.huawei.scan

import java.util.concurrent.CountDownLatch
import org.luckypray.dexkit.DexKitBridge

/** 一次宿主启动的两个扫描阶段共用 base/split 桥；入口先查询，双方结束后释放。 */
internal class HostDexSession(private val paths: List<String>,
    private val loadNative: () -> Unit = { System.loadLibrary("dexkit") }) {
    private val lock = Any()
    private val entryFinished = CountDownLatch(1)
    private val bridges = mutableListOf<DexKitBridge>()
    private var layoutFinished = false

    fun <T> entry(query: (List<DexKitBridge>) -> T): T = try {
        withBridges(query)
    } finally { finishEntry() }

    fun <T> layout(query: (List<DexKitBridge>) -> T): T {
        entryFinished.await()
        return withBridges(query)
    }

    private fun <T> withBridges(action: (List<DexKitBridge>) -> T): T = synchronized(lock) {
        check(!layoutFinished || entryFinished.count != 0L)
        if (bridges.isEmpty()) {
            loadNative()
            try {
                paths.forEach { bridges += DexKitBridge.create(it) }
            } catch (error: Throwable) {
                close()
                throw error
            }
        }
        action(bridges)
    }

    fun finishEntry() = synchronized(lock) {
        entryFinished.countDown()
        if (layoutFinished) close()
    }

    fun finishLayout() = synchronized(lock) {
        layoutFinished = true
        if (entryFinished.count == 0L) close()
    }

    private fun close() {
        bridges.forEach { runCatching { it.close() } }
        bridges.clear()
    }
}
