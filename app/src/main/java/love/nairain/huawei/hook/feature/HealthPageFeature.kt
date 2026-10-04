package love.nairain.huawei.hook.feature

import android.view.View
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.HookFeature
import love.nairain.huawei.hook.InstallResult
import love.nairain.huawei.hook.installSource
import love.nairain.huawei.hook.resolver.ListFilters
import love.nairain.huawei.hook.util.ViewTrimmer

class HealthPageFeature : HookFeature {
    override val id = "health.page"

    override fun install(context: HookContext): InstallResult {
        if (!SettingsCatalog.hasHidden(SettingsCategory.HEALTH, context.config)) {
            return InstallResult.Disabled
        }
        var count = 0
        count += context.installSource("health.top", ::installTopControls)
        count += context.installSource("health.top-cards", ::installTopCardFilter)
        count += context.installSource("health.edit-cards", ::installEditCards)
        return if (count > 0) InstallResult.Installed(count)
        else InstallResult.Unsupported("verified health symbols unavailable")
    }

    private fun installTopControls(context: HookContext): Int {
        val type = context.targets.type(context.classLoader, context.points.homeFragment) ?: return 0
        var count = 0
        context.targets.method(type, "onCreateView", 3)?.let { method ->
            context.hooks.hook(method).setId("$id:top:create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    (result as? View)?.let { applyTopControls(it, context) }
                    result
                }
            count++
        }
        context.targets.method(type, "onResume", 0, Void.TYPE)?.let { method ->
            context.hooks.hook(method).setId("$id:top:resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    (context.targets.invokeNoArgs(chain.thisObject, "getView") as? View)
                        ?.let { applyTopControls(it, context) }
                    result
                }
            count++
        }
        return count
    }

    private fun installTopCardFilter(context: HookContext): Int {
        val type = context.targets.type(context.classLoader, context.points.homeAdapter) ?: return 0
        var count = 0
        context.targets.constructor(type, 2)?.let { constructor ->
            context.hooks.hook(constructor).setId("$id:adapter:init")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val args = filteredArguments(chain.args, 1, context) { item ->
                        (context.targets.invokeNoArgs(item, "card.identity") as? String)?.let { identity ->
                            context.targets.identities["card:${item.javaClass.name}:$identity"]
                        }
                    }
                    chain.proceed(args)
                }
            count++
        }
        context.targets.method(type, "cards.refresh", 1)?.let { method ->
            context.hooks.hook(method).setId("$id:adapter:refresh")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val args = filteredArguments(chain.args, 0, context) { item ->
                        (context.targets.invokeNoArgs(item, "card.identity") as? String)?.let { identity ->
                            context.targets.identities["card:${item.javaClass.name}:$identity"]
                        }
                    }
                    chain.proceed(args)
                }
            count++
        }
        return count
    }

    private fun installEditCards(context: HookContext): Int {
        val type = context.targets.type(context.classLoader, context.points.functionSetHolder) ?: return 0
        context.targets.method(type, "edit.update", 0, Void.TYPE)?.let { method ->
            context.hooks.hook(method).setId("$id:edit-cards")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    if (context.config[SettingsKeys.HEALTH_EDIT_CARDS] == true) {
                        (context.targets.fieldValue(chain.thisObject, "edit.root") as? View)?.let { root ->
                            context.views.findByResourceName(root, context.application.packageName, "modify_cards_layout")
                                ?.let(ViewTrimmer::collapse)
                        }
                    }
                    result
                }
            return 1
        }
        return 0
    }

    private fun applyTopControls(root: View, context: HookContext) {
        val titleBar = context.views.findByResourceName(
            root,
            context.application.packageName,
            "health_tab_titlebar",
        ) ?: return
        if (context.config[SettingsKeys.HEALTH_SEARCH] == true) {
            hideControl(titleBar, "setRightSoftkeyVisibility", context)
        }
        if (context.config[SettingsKeys.HEALTH_MORE] == true) {
            hideControl(titleBar, "setRightButtonVisibility", context)
        }
    }

    private fun hideControl(target: View, methodName: String, context: HookContext) {
        runCatching {
            context.targets.method(target.javaClass, methodName, 1)?.invoke(target, View.GONE)
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
