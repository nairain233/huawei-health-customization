package love.nairain.huawei.hook.feature

import android.view.View
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.HookFeature
import love.nairain.huawei.hook.InstallResult
import love.nairain.huawei.hook.installIsolated
import love.nairain.huawei.hook.resolver.HealthContentKeyResolver
import love.nairain.huawei.hook.resolver.ListFilters
import love.nairain.huawei.hook.resolver.ReflectionTargets
import love.nairain.huawei.hook.util.ViewSelectors
import love.nairain.huawei.hook.util.ViewTrimmer

class HealthPageFeature : HookFeature {
    override val id = "health.page"

    override fun install(context: HookContext): InstallResult {
        if (!SettingsCatalog.hasHidden(SettingsCategory.HEALTH, context.config)) {
            return InstallResult.Disabled
        }
        var count = 0
        count += context.installIsolated("health.top") { installTopControls(context) }
        count += context.installIsolated("health.top-cards") { installTopCardFilter(context) }
        count += context.installIsolated("health.edit-cards") { installEditCards(context) }
        return if (count > 0) InstallResult.Installed(count)
        else InstallResult.Unsupported("verified health symbols unavailable")
    }

    private fun installTopControls(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader, context.points.homeFragment) ?: return 0
        var count = 0
        ReflectionTargets.method(type, "onCreateView", 3)?.let { method ->
            context.hooks.hook(method).setId("$id:top:create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    (result as? View)?.let { applyTopControls(it, context) }
                    result
                }
            count++
        }
        ReflectionTargets.method(type, "onResume", 0, Void.TYPE)?.let { method ->
            context.hooks.hook(method).setId("$id:top:resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    (ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View)
                        ?.let { applyTopControls(it, context) }
                    result
                }
            count++
        }
        return count
    }

    private fun installTopCardFilter(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader, context.points.homeAdapter) ?: return 0
        var count = 0
        ReflectionTargets.constructor(type, 2)?.let { constructor ->
            context.hooks.hook(constructor).setId("$id:adapter:init")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val args = filteredArguments(chain.args, 1, context) { item ->
                        HealthContentKeyResolver.topCard(
                            ReflectionTargets.invokeNoArgs(item, "getCardName") as? String,
                        )
                    }
                    chain.proceed(args)
                }
            count++
        }
        ReflectionTargets.method(type, "c", 1)?.let { method ->
            context.hooks.hook(method).setId("$id:adapter:refresh")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val args = filteredArguments(chain.args, 0, context) { item ->
                        HealthContentKeyResolver.topCard(
                            ReflectionTargets.invokeNoArgs(item, "getCardName") as? String,
                        )
                    }
                    chain.proceed(args)
                }
            count++
        }
        return count
    }

    private fun installEditCards(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader, context.points.functionSetHolder) ?: return 0
        ReflectionTargets.method(type, "l", 0, Void.TYPE)?.let { method ->
            context.hooks.hook(method).setId("$id:edit-cards")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    if (context.config[SettingsKeys.HEALTH_EDIT_CARDS] == true) {
                        (ReflectionTargets.fieldValue(chain.thisObject, "m") as? View)
                            ?.let(ViewTrimmer::collapse)
                    }
                    result
                }
            return 1
        }
        return 0
    }

    private fun applyTopControls(root: View, context: HookContext) {
        val titleBar = ViewSelectors.findByResourceName(
            root,
            context.application.packageName,
            "health_tab_titlebar",
        ) ?: return
        if (context.config[SettingsKeys.HEALTH_SEARCH] == true) {
            hideControl(titleBar, "setRightSoftkeyVisibility")
        }
        if (context.config[SettingsKeys.HEALTH_MORE] == true) {
            hideControl(titleBar, "setRightButtonVisibility")
        }
    }

    private fun hideControl(target: View, methodName: String) {
        runCatching {
            target.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
                .invoke(target, View.GONE)
        }
    }

    private fun filteredArguments(
        args: List<Any>,
        index: Int,
        context: HookContext,
        keyOf: (Any) -> String?,
    ): Array<Any> {
        val replacement = args.toTypedArray()
        val source = args.getOrNull(index) as? List<*> ?: return replacement
        replacement[index] = ListFilters.copyAndFilter(source.filterNotNull(), keyOf, context.config)
        return replacement
    }

}
