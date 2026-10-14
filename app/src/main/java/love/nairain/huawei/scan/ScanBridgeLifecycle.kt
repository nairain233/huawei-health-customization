package love.nairain.huawei.scan

import java.util.concurrent.Executor

/** 页面与框架对象均按身份隔离；过期的异步打开结果只关闭自身资源。 */
internal class ScanBridgeLifecycle(
    private val worker: Executor,
    private val open: (Any, () -> Boolean) -> AutoCloseable,
    private val failure: (Throwable) -> Unit,
) {
    private class Session(val owner: Any, val binding: Any) {
        @Volatile var active = true
        private var handle: AutoCloseable? = null
        fun attach(value: AutoCloseable) {
            val stale = synchronized(this) { if (active) { handle = value; false } else true }
            if (stale) value.close()
        }
        fun close() {
            val old = synchronized(this) { active = false; handle.also { handle = null } }
            old?.close()
        }
    }
    private var owner: Any? = null
    private var session: Session? = null

    // 三个入口由主线程调用；后台只读取 Session.active。
    fun start(owner: Any, binding: Any?) {
        this.owner = owner
        bind(owner, binding)
    }
    fun bind(owner: Any, binding: Any?) {
        if (this.owner !== owner) return
        if (session?.let { it.owner === owner && it.binding === binding } == true) return
        session?.close()
        session = null
        if (binding == null) return
        val next = Session(owner, binding)
        session = next
        worker.execute {
            if (next.active) try {
                next.attach(open(binding) { next.active })
            } catch (error: Exception) {
                next.close()
                failure(error)
            }
        }
    }
    fun stop(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        session?.close()
        session = null
    }
}

/** 只在端点执行器调用；绑定重建时旧偏好缓存可能滞后，因此先校验发布者身份。 */
internal class ScanBridgePublication {
    private var owner: Any? = null

    @android.annotation.SuppressLint("UseKtx") // 发布失败会由接收器关闭本会话。
    fun publish(owner: Any, prefs: android.content.SharedPreferences, endpoint: ScanBridgeProtocol.Endpoint) {
        this.owner = owner
        check(prefs.edit().putString(ScanBridgeProtocol.ENDPOINT, endpoint.encode()).commit())
    }

    @android.annotation.SuppressLint("UseKtx") // 清理失败只允许残留已失效端点。
    fun clear(owner: Any, prefs: android.content.SharedPreferences, endpoint: ScanBridgeProtocol.Endpoint) {
        if (this.owner !== owner) return
        this.owner = null
        if (prefs.getString(ScanBridgeProtocol.ENDPOINT, null) == endpoint.encode()) {
            check(prefs.edit().remove(ScanBridgeProtocol.ENDPOINT).commit())
        }
    }
}
