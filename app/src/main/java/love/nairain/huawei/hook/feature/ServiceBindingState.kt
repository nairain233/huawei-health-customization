package love.nairain.huawei.hook.feature

import java.lang.ref.WeakReference

/** 按外层 Context 和连接的对象身份隔离；值中也不能强引用宿主对象。 */
internal class ServiceBindingState {
    private class Entry(context: Any, connection: Any) {
        val context = WeakReference(context)
        val connection = WeakReference(connection)
        var blocked = false
        var forwarded = false
    }

    private val entries = mutableListOf<Entry>()

    @Synchronized
    fun record(context: Any, connection: Any, blocked: Boolean) {
        prune()
        val entry = entries.firstOrNull { it.context.get() === context && it.connection.get() === connection }
            ?: Entry(context, connection).also(entries::add)
        if (blocked) entry.blocked = true else entry.forwarded = true
    }

    /** 真实调用即使返回 false 也可能注册了 dispatcher，因此仍交给系统解绑。 */
    @Synchronized
    fun consumeBlockedUnbind(context: Any, connection: Any): Boolean {
        prune()
        val entry = entries.firstOrNull { it.context.get() === context && it.connection.get() === connection }
            ?: return false
        entries.remove(entry)
        return entry.blocked && !entry.forwarded
    }

    private fun prune() {
        entries.removeAll { it.context.get() == null || it.connection.get() == null }
    }
}
