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
            val targets = LocalWatchFaceTargets(loader, version, code)
            val runtime = LocalWatchFaceRuntime(app, targets, logger, serviceConflict)
            val hooks = TransactionalHooks(framework, logger)
            hooks.install("watchface.local") {
                val scope = hooks.callbacks()
                scope.onClose { runtime.close() }
                var installed = 0
                fun hook(key: String, action: XposedInterface.Hooker) {
                    hooks.hook(targets.method(key)).setId("watchface.local:$key")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(action)
                    installed++
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
                    runtime.nativeBtResponse(chain.thisObject, chain.args[0] as Int, chain.args[1]) { chain.proceed() }
                }
                hook("report") { chain ->
                    runtime.guarded { runtime.missingIdentityError(chain.args[1] as Int, chain.args[2] as? String) }
                    chain.proceed()
                }
                hook("blockApply") { chain ->
                    if (runtime.blockOtherApply(chain.thisObject, chain.args)) null else chain.proceed()
                }
                hook("operate") { chain ->
                    if (runtime.blockOtherOperation(chain.thisObject, chain.args)) null else chain.proceed()
                }
                hook("nativeCommand") { chain ->
                    runtime.guarded { runtime.nativeCommand(chain.thisObject, chain.args) }
                    chain.proceed()
                }
                hook("apiTransfer") { chain ->
                    runtime.guarded { runtime.apiTransfer(chain.args) }
                    chain.proceed()
                }
                hook("stopDispatch") { chain ->
                    runtime.guarded { runtime.nativeStopDispatch(chain.thisObject, chain.args) }
                    chain.proceed()
                }
                hook("nativeSignatureStep") { chain ->
                    runtime.nativeStep(chain.thisObject, chain.args, true) { chain.proceed() }
                }
                hook("nativeContinue") { chain ->
                    runtime.nativeStep(chain.thisObject, chain.args, false) { chain.proceed() }
                }
                hook("putCache") { chain ->
                    if (runtime.cachePayload(chain.args[0] as? String, chain.args[1])) chain.proceed() else null
                }
                hook("requestSignature") { chain ->
                    val result = chain.proceed()
                    runtime.guarded { runtime.signatureResponse(chain.args[0] as? String, chain.args[1] as? String) }
                    result
                }
                hook("managerTransfer") { chain ->
                    runtime.nativeTransfer(chain.thisObject, chain.args) { args -> chain.proceed(args.toTypedArray()) }
                }
                hook("transfer") { chain ->
                    runtime.nativeFileTransfer(chain.thisObject, chain.args) { chain.proceed() }
                }
                hook("installResponse") { chain ->
                    runtime.nativeInstallResponse(chain.thisObject, chain.args[0] as Int, chain.args[1]) { chain.proceed() }
                }
                listOf("fileProgressHandler", "fileResultHandler", "fileFailureHandler").forEach { key ->
                    hook(key) { chain ->
                        runtime.guarded { runtime.fileEvent(chain.thisObject, key, chain.args[0] as Int) }
                        chain.proceed()
                    }
                }
                hook("stopResponse") { chain ->
                    runtime.nativeStopResponse(chain.thisObject, chain.args[0] as Int, chain.args[1]) { chain.proceed() }
                }
                hook("names") { chain ->
                    runtime.guarded { runtime.fillNames(chain.args[0]) }
                    chain.proceed()
                }
                scope.afterActivation { runtime.initialize() }
                installed
            }
            logger.info("Local watch face hooks installed; device validation pending")
        } catch (error: Throwable) {
            logger.warn("Local watch face unavailable: ${error.javaClass.simpleName}")
        }
    }
}
