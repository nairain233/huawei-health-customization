package love.nairain.huawei.hook.util

import java.util.concurrent.atomic.AtomicBoolean

/** 每个安装组独立管理异步任务：成功后激活，失败后撤销且只记录一次脱敏原因。 */
class LayoutCallbackScope(private val reportFailure: (String) -> Unit) : AutoCloseable {
    private enum class State { WAITING, ACTIVE, CLOSED }
    private val lock = Any()
    @Volatile private var state = State.WAITING
    private val activations = mutableListOf<() -> Unit>()
    private val cleanups = linkedSetOf<() -> Unit>()
    private val reported = AtomicBoolean()

    val isActive: Boolean get() = state == State.ACTIVE

    fun afterActivation(action: () -> Unit) {
        val ready = synchronized(lock) {
            when (state) {
                State.WAITING -> { activations += action; false }
                State.ACTIVE -> true
                State.CLOSED -> false
            }
        }
        if (ready) run(action)
    }

    fun activate() {
        val actions = synchronized(lock) {
            if (state != State.WAITING) return
            state = State.ACTIVE
            activations.toList().also { activations.clear() }
        }
        actions.forEach(::run)
    }

    fun run(action: () -> Unit) {
        if (!isActive) return
        try { action() } catch (error: Throwable) {
            report(error)
            close()
        }
    }

    /** 返回注销函数，已执行或页面已脱离的任务不继续占用组的撤销列表。 */
    fun onClose(cleanup: () -> Unit): () -> Unit {
        val closed = synchronized(lock) {
            if (state == State.CLOSED) true else { cleanups += cleanup; false }
        }
        if (closed) this.cleanup(cleanup)
        return { synchronized(lock) { cleanups.remove(cleanup) } }
    }

    override fun close() {
        val actions = synchronized(lock) {
            if (state == State.CLOSED) return
            state = State.CLOSED
            activations.clear()
            cleanups.toList().asReversed().also { cleanups.clear() }
        }
        actions.forEach(::cleanup)
    }

    internal fun cleanup(action: () -> Unit) {
        try { action() } catch (error: Throwable) { report(error) }
    }

    private fun report(error: Throwable) {
        if (!reported.compareAndSet(false, true)) return
        try { reportFailure(error.javaClass.simpleName) } catch (diagnostic: Throwable) {
            // 日志服务自身失败也不能传播到宿主，不输出原始异常消息或栈。
            System.err.println("HuaweiTrim: Callback diagnostic unavailable: ${diagnostic.javaClass.simpleName}")
        }
    }
}
