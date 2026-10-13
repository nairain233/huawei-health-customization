package love.nairain.huawei.hook.feature

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.resolver.BadgeTargets
import love.nairain.huawei.hook.util.ActivePageObserver
import love.nairain.huawei.hook.util.applyPage

/** 仅处理 HealthBottomView 的绘制状态，不改变页签身份或宿主业务未读状态。 */
internal object BottomBadgeHooks {
    fun install(context: HookContext): Int {
        if (context.config[SettingsKeys.BOTTOM_BADGES] != true) return 0
        val base = requireNotNull(context.targets.type(context.classLoader, context.points.bottomBase))
        val item = requireNotNull(context.targets.type(context.classLoader, BadgeTargets.BOTTOM_ITEM))
        val update = requireNotNull(context.targets.method(base, "badges.update", 2, Void.TYPE))
        val setter = requireNotNull(context.targets.method(item, "setHasMessage", 1, Void.TYPE))
        val getter = requireNotNull(context.targets.method(item, "badges.current", 0, Boolean::class.javaPrimitiveType))
        val layout = requireNotNull(context.targets.method(base, "onLayout", 5, Void.TYPE))
        val scope = context.hooks.callbacks()
        val requested = BottomBadgeState<View>(context.points.bottomView)
        scope.onClose {
            val restore = {
                requested.snapshot().forEach { (child, visible) -> scope.cleanup { setter.invoke(child, visible) } }
                requested.clear()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) restore()
            else Handler(Looper.getMainLooper()).post(restore)
        }
        context.hooks.hook(update).setId("bottom.badges:update")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val view = chain.thisObject as? ViewGroup
                val index = chain.args.firstOrNull() as? Int
                var args = chain.args.toTypedArray()
                if (view?.javaClass?.name == context.points.bottomView && index != null &&
                    Looper.myLooper() == Looper.getMainLooper()) scope.run {
                    view.getChildAt(index)?.takeIf(item::isInstance)?.let { child ->
                        val visible = requested.update(view.javaClass.name, child, chain.args[1] as Boolean)
                        args = args.copyOf().apply { this[1] = visible }
                    }
                }
                chain.proceed(args)
            }
        fun hide(navigation: ViewGroup) {
            for (index in 0 until navigation.childCount) {
                val child = navigation.getChildAt(index)
                if (item.isInstance(child)) {
                    val visible = getter.invoke(child) as Boolean
                    val suppressed = requested.replay(navigation.javaClass.name, child, visible)
                    if (visible != suppressed) setter.invoke(child, suppressed)
                }
            }
        }
        context.hooks.hook(layout).setId("bottom.badges:layout")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? ViewGroup)?.takeIf { it.javaClass.name == context.points.bottomView }?.let { view ->
                    scope.applyPage(view) { hide(it as ViewGroup) }
                }
                result
            }
        ActivePageObserver.observe(scope) { activity ->
            context.views.walk(activity.window.decorView) { view ->
                if (view is ViewGroup && view.javaClass.name == context.points.bottomView) {
                    scope.applyPage(view) { root ->
                        hide(root as ViewGroup)
                    }
                }
            }
        }
        return 2
    }
}
