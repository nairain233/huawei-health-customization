package love.nairain.huawei.hook.feature

/** 三个入口作为一个安装事务；回滚失败的入口通过 active=false 保持放行。 */
internal class ServiceHookInstallation {
    @Volatile
    var active = false
        private set
    private val removals = mutableListOf<() -> Unit>()

    fun register(remove: () -> Unit) { removals += remove }

    fun install(onFailure: (Throwable) -> Unit, block: ServiceHookInstallation.() -> Unit) {
        try {
            block()
            active = true
        } catch (error: Throwable) {
            active = false
            removals.asReversed().forEach { remove ->
                try { remove() } catch (rollback: Throwable) { onFailure(rollback) }
            }
            onFailure(error)
        } finally {
            removals.clear()
        }
    }
}
