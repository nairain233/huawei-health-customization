package love.nairain.huawei.hook.feature

import android.app.Activity
import android.content.Context
import android.content.Intent
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.hook.TransactionalHooks
import love.nairain.huawei.hook.resolver.LocalWatchFaceTargets
import love.nairain.huawei.hook.util.ModuleLogger
import love.nairain.huawei.watchface.LocalWatchFaceRuntime

/** 仅主进程 attach 后调用，默认关闭；精确版本及全部契约通过后才安装入口。 */
internal object LocalWatchFaceFeature {
    fun install(framework: XposedInterface, loader: ClassLoader, app: Context,
        version: String?, code: Long, enabled: Boolean, serviceConflict: Boolean, logger: ModuleLogger) {
        if (!LocalWatchFaceTargets.accepts(version, code, enabled)) return
        try {
            val targets = LocalWatchFaceTargets(loader)
            val runtime = LocalWatchFaceRuntime(app, targets, logger, serviceConflict)
            val hooks = TransactionalHooks(framework, logger)
            hooks.install("watchface.local") {
                val scope = hooks.callbacks()
                scope.onClose { runtime.close() }
                fun hook(key: String, action: XposedInterface.Hooker) {
                    hooks.hook(targets.method(key)).setId("watchface.local:$key")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(action)
                }
                hook("page") { chain ->
                    val result = chain.proceed()
                    scope.run { runtime.attach(chain.thisObject as Activity) }
                    result
                }
                hook("destroy") { chain ->
                    scope.run { runtime.detach(chain.thisObject as Activity) }
                    chain.proceed()
                }
                hook("pageLoaded") { chain ->
                    val result = chain.proceed()
                    scope.run { runtime.pageLoaded(chain.args[0] as android.webkit.WebView) }
                    result
                }
                hook("navigate") { chain ->
                    var consumed = false
                    runtime.guarded { consumed = runtime.navigate(chain.args[0] as android.webkit.WebView, chain.args[1] as? String) }
                    if (consumed) true else chain.proceed()
                }
                hook("result") { chain ->
                    var consumed = false
                    runtime.guarded { consumed = runtime.result(chain.thisObject as Activity,
                        chain.args[0] as Int, chain.args[1] as Int, chain.args[2] as? Intent) }
                    if (consumed) null else chain.proceed()
                }
                hook("listed") { chain ->
                    runtime.guarded { runtime.listed() }
                    chain.proceed()
                }
                hook("btResponse") { chain ->
                    var consumed = false
                    runtime.guarded { consumed = runtime.btResponse(chain.args[0] as Int, chain.args[1]) }
                    if (consumed) null else chain.proceed()
                }
                hook("report") { chain ->
                    var consumed = false
                    runtime.guarded { consumed = runtime.missingIdentityError(chain.args[1] as Int, chain.args[2] as? String) }
                    if (consumed) null else chain.proceed()
                }
                hook("blockApply") { chain ->
                    if (runtime.blockOtherApply()) { runtime.notifyBusy(); null } else chain.proceed()
                }
                hook("operate") { chain ->
                    if (runtime.blockOtherOperation()) { runtime.notifyBusy(); null } else chain.proceed()
                }
                hook("names") { chain ->
                    runtime.guarded { runtime.fillNames(chain.args[0]) }
                    chain.proceed()
                }
                scope.afterActivation { runtime.initialize() }
                11
            }
            logger.info("Local watch face hooks installed; device validation pending")
        } catch (error: Throwable) {
            logger.warn("Local watch face unavailable: ${error.javaClass.simpleName}")
        }
    }
}
