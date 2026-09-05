package love.nairain.huawei.hook.feature

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.HookFeature
import love.nairain.huawei.hook.InstallResult
import love.nairain.huawei.hook.installIsolated
import love.nairain.huawei.hook.resolver.BottomTabKeyResolver
import love.nairain.huawei.hook.resolver.ReflectionTargets
import love.nairain.huawei.hook.util.ViewTrimmer

class BottomTabFeature : HookFeature {
    override val id = "bottom.tabs"
    private val resolver = BottomTabKeyResolver()
    private val state = BottomTabStateStore<ViewGroup>()

    override fun install(context: HookContext): InstallResult {
        if (!SettingsCatalog.hasHidden(SettingsCategory.BOTTOM, context.config)) {
            return InstallResult.Disabled
        }
        val base = ReflectionTargets.type(context.classLoader, context.points.bottomBase)
            ?: return InstallResult.Unsupported("bottom navigation base unavailable")
        val clear = ReflectionTargets.method(base, "a", 0, Void.TYPE)
        val add = ReflectionTargets.methods(base, "a", 3).firstOrNull { method ->
            method.name == "a" && method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.contentEquals(
                    arrayOf(Int::class.javaPrimitiveType, Drawable::class.java, Boolean::class.javaPrimitiveType),
                )
        }?.apply { isAccessible = true }
        val layout = ReflectionTargets.method(base, "onLayout", 5, Void.TYPE)

        var count = 0
        if (clear != null) count += context.installIsolated("$id.clear") {
            context.framework.hook(clear).setId("$id:clear")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? ViewGroup)?.takeIf { isMainView(it, context) }?.let {
                        state.clear(it)
                    }
                    result
                }
            1
        }
        if (add != null) count += context.installIsolated("$id.add") {
            context.framework.hook(add).setId("$id:add")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    recordTab(chain.thisObject as? ViewGroup, chain.args.firstOrNull() as? Int, context)
                    result
                }
            1
        }
        if (layout != null) count += context.installIsolated("$id.layout") {
            context.framework.hook(layout).setId("$id:layout")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    applyLayout(chain.thisObject as? ViewGroup, context)
                    result
                }
            1
        }
        return if (count > 0) InstallResult.Installed(count)
        else InstallResult.Unsupported("bottom methods unavailable")
    }

    private fun recordTab(view: ViewGroup?, titleId: Int?, context: HookContext) {
        if (view == null || titleId == null || titleId <= 0 || !isMainView(view, context)) return
        val child = view.getChildAt(view.childCount - 1) ?: return
        val index = itemIndex(child) ?: return
        val resourceName = runCatching { view.resources.getResourceEntryName(titleId) }.getOrNull()
        val key = resolver.resolve(resourceName) ?: return
        val hidden = context.config[key] == true
        state.record(view, index, hidden)
        if (hidden) setItemEnabled(view, index, false)
    }

    private fun applyLayout(view: ViewGroup?, context: HookContext) {
        if (view == null || !isMainView(view, context) || view.width <= 0 || view.height <= 0) return
        val hiddenIndexes = state.snapshot(view)
        val visible = ArrayList<View>()
        for (position in 0 until view.childCount) {
            val child = view.getChildAt(position) ?: continue
            val index = itemIndex(child) ?: continue
            val key = BottomTabIndexResolver.resolve(index)
            val hiddenByConfig = key != null && context.config[key] == true
            state.record(view, index, hiddenByConfig)
            if (hiddenByConfig || index in hiddenIndexes) {
                ViewTrimmer.collapse(child)
                setItemEnabled(view, index, false)
            } else {
                ViewTrimmer.restore(child)
                setItemEnabled(view, index, true)
                visible += child
            }
        }
        if (visible.isEmpty()) return // 配置归一化已保证至少保留“健康”。
        val available = view.width - view.paddingLeft - view.paddingRight
        if (available <= 0) return
        val ordered = BottomLayoutOrder.arrange(
            visible,
            view.layoutDirection == View.LAYOUT_DIRECTION_RTL,
        )
        val baseWidth = available / ordered.size
        val remainder = available % ordered.size
        var left = view.paddingLeft
        ordered.forEachIndexed { position, child ->
            val width = baseWidth + if (position < remainder) 1 else 0
            child.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                    view.height - view.paddingTop - view.paddingBottom,
                    View.MeasureSpec.EXACTLY,
                ),
            )
            child.layout(left, view.paddingTop, left + width, view.height - view.paddingBottom)
            left += width
        }
    }

    private fun itemIndex(child: View): Int? = runCatching {
        child.javaClass.getMethod("getItemIndex").invoke(child) as? Int
    }.getOrNull()

    private fun setItemEnabled(view: ViewGroup, index: Int, enabled: Boolean) {
        runCatching {
            view.javaClass.getMethod(
                "setSelectItemEnabled",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ).invoke(view, index, enabled)
        }
    }

    private fun isMainView(view: ViewGroup, context: HookContext): Boolean =
        view.javaClass.name == context.points.bottomView
}
