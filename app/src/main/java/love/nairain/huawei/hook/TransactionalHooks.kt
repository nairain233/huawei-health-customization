package love.nairain.huawei.hook

import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.hook.util.ModuleLogger
import love.nairain.huawei.hook.util.LayoutCallbackScope
import java.lang.reflect.Executable

/** 注册期间回调保持放行；失败时先禁用整组，再撤销已注册入口。 */
class TransactionalHooks(private val framework: XposedInterface, private val logger: ModuleLogger) {
    private class Group(val callbacks: LayoutCallbackScope) {
        val handles = mutableListOf<XposedInterface.HookHandle>()
    }
    private var current: Group? = null

    fun callbacks(): LayoutCallbackScope = checkNotNull(current).callbacks

    fun install(source: String = "layout", block: () -> Int): Int {
        check(current == null)
        val group = Group(LayoutCallbackScope { type -> logger.warn("Layout callbacks disabled: $source, $type") })
        current = group
        try {
            val count = block()
            if (count > 0) group.callbacks.activate() else group.callbacks.close()
            if (!group.callbacks.isActive) rollback(group)
            return if (group.callbacks.isActive) count else 0
        } catch (error: Throwable) {
            group.callbacks.close()
            rollback(group)
            throw error
        } finally { current = null; group.handles.clear() }
    }

    private fun rollback(group: Group) {
        group.handles.asReversed().forEach {
            try { it.unhook() } catch (rollback: Throwable) {
                logger.warn("Layout rollback failed: ${rollback.javaClass.simpleName}")
            }
        }
    }

    fun hook(method: Executable): XposedInterface.HookBuilder {
        val group = checkNotNull(current)
        val delegate = framework.hook(method)
        return object : XposedInterface.HookBuilder {
            override fun setPriority(priority: Int): XposedInterface.HookBuilder { delegate.setPriority(priority); return this }
            override fun setId(id: String?): XposedInterface.HookBuilder { delegate.setId(id); return this }
            override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder { delegate.setExceptionMode(mode); return this }
            override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle = delegate.intercept {
                if (group.callbacks.isActive) hooker.intercept(it) else it.proceed()
            }.also { group.handles += it }
        }
    }
}
