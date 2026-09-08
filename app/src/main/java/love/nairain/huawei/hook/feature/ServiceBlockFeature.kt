package love.nairain.huawei.hook.feature

import android.content.Context
import android.annotation.SuppressLint
import android.content.Intent
import android.content.ServiceConnection
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.hook.HookInstallPolicy
import love.nairain.huawei.hook.resolver.ServiceHookTargets
import love.nairain.huawei.hook.util.ModuleLogger

/** 仅在目标主进程拦截显式服务调用；默认关闭，安装不完整则全部回滚。 */
internal object ServiceBlockFeature {
    @SuppressLint("PrivateApi") // LSPosed 进程内 Hook；先核验签名，未知实现整项回滚并放行。
    fun install(
        framework: XposedInterface,
        config: ServiceBlockConfig,
        declared: Set<String>,
        logger: ModuleLogger,
    ): String {
        if (!config.enabled || config.components.intersect(declared).isEmpty()) return "disabled"
        val bindings = ServiceBindingState()
        val installation = ServiceHookInstallation()
        installation.install(onFailure = { error ->
            // 不输出异常消息和栈，避免宿主参数间接出现在日志中。
            logger.warn("Service blocking skipped or rollback failed: ${error.javaClass.simpleName}")
        }) {
            val targets = ServiceHookTargets.resolve(Class.forName("android.app.ContextImpl"))
            fun isTarget(chain: XposedInterface.Chain): Boolean = installation.active &&
                (chain.thisObject as? Context)?.packageName == HookInstallPolicy.TARGET_PACKAGE
            fun blocked(chain: XposedInterface.Chain): Boolean {
                val intent = chain.args[0] as? Intent ?: return false
                val component = intent.component ?: return false
                return config.blocks(component.packageName, component.className, declared)
            }
            val start = framework.hook(targets.start).setId("huawei.service.start")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                    if (isTarget(chain) && blocked(chain)) null else chain.proceed()
                }
            register { start.unhook() }
            val bind = framework.hook(targets.bind).setId("huawei.service.bind")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                    val connection = chain.args[1] as? ServiceConnection
                    if (!isTarget(chain) || connection == null) {
                        chain.proceed()
                    } else {
                        val context = targets.outerContext.invoke(chain.thisObject)
                        if (context == null) chain.proceed() else {
                            val shouldBlock = blocked(chain)
                            bindings.record(context, connection, shouldBlock)
                            if (shouldBlock) false else chain.proceed()
                        }
                    }
                }
            register { bind.unhook() }
            val unbind = framework.hook(targets.unbind).setId("huawei.service.unbind")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                    val connection = chain.args[0] as? ServiceConnection
                    val context = if (isTarget(chain) && connection != null) {
                        targets.outerContext.invoke(chain.thisObject)
                    } else null
                    val blockedOnly = context != null && connection != null &&
                        bindings.consumeBlockedUnbind(context, connection)
                    // 先尝试真实解绑，兼容 attach 之前已存在或其他途径注册的连接。
                    try {
                        chain.proceed()
                    } catch (error: IllegalArgumentException) {
                        if (blockedOnly) null else throw error
                    }
                }
            register { unbind.unhook() }
            // 公共包装方法可能已内联 common 入口，去优化这些已知调用者以确保进入 Hook。
            targets.callers.forEach {
                check(framework.deoptimize(it)) { "Service caller deoptimization failed" }
            }
        }
        if (installation.active) logger.info("Service blocking installed")
        return if (installation.active) "installed" else "unsupported"
    }
}
