package love.nairain.huawei.hook

import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.hook.util.ModuleLogger
import java.lang.reflect.Executable

/** 注册期间回调保持放行；失败时先禁用整组，再撤销已注册入口。 */
class TransactionalHooks(private val framework: XposedInterface, private val logger: ModuleLogger) {
    private class Group {
        @Volatile var active = false
        val handles = mutableListOf<XposedInterface.HookHandle>()
    }
    private var current: Group? = null

    fun install(block: () -> Int): Int {
        check(current == null)
        val group = Group()
        current = group
        try {
            val count = block()
            group.active = true
            return count
        } catch (error: Throwable) {
            group.handles.asReversed().forEach {
                try { it.unhook() } catch (rollback: Throwable) {
                    logger.warn("Layout rollback failed: ${rollback.javaClass.simpleName}")
                }
            }
            throw error
        } finally { current = null; group.handles.clear() }
    }

    fun hook(method: Executable): XposedInterface.HookBuilder {
        val group = checkNotNull(current)
        val delegate = framework.hook(method)
        return object : XposedInterface.HookBuilder {
            override fun setPriority(priority: Int): XposedInterface.HookBuilder { delegate.setPriority(priority); return this }
            override fun setId(id: String?): XposedInterface.HookBuilder { delegate.setId(id); return this }
            override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder { delegate.setExceptionMode(mode); return this }
            override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle = delegate.intercept {
                if (group.active) hooker.intercept(it) else it.proceed()
            }.also { group.handles += it }
        }
    }
}
