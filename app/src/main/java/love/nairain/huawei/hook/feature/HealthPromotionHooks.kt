package love.nairain.huawei.hook.feature

import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.util.ActivePageObserver
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 默认关闭；只关闭已核验 DailyMoment 的整卡展示，不修改营销数据、请求或关闭记录。 */
internal object HealthPromotionHooks {
    fun install(context: HookContext): Int {
        if (context.config[SettingsKeys.HEALTH_PROMOTION] != true) return 0
        val gate = context.targets.methodForRole("promotion.visible") ?: return 0
        val home = context.targets.type(context.classLoader, context.points.homeFragment) ?: return 0
        val getAdapter = context.targets.methodForRole("getAdapter") ?: return 0
        val computing = context.targets.methodForRole("isComputingLayout") ?: return 0
        val refresh = context.targets.methodForRole("notifyDataSetChanged") ?: return 0
        val notification = context.targets.methodForRole("promotion.notification") ?: return 0
        val itemNotifications = listOf("notifyItemInserted", "notifyItemRemoved").map { role ->
            context.targets.methodForRole(role) ?: return 0
        }
        val scopes = context.resolution.bindings.keys.map { it.substringAfter('#').substringBefore('#') }
            .filter { it.startsWith("promotion.scope.") }.map { role ->
                context.targets.methodForRole(role) ?: return 0
            }
        if (scopes.isEmpty()) return 0
        val adapterType = context.targets.type(context.classLoader,
            "com.huawei.health.functionsetcard.FunctionSetViewAdapter") ?: return 0
        val callbacks = context.hooks.callbacks()
        val refreshed = WeakHashMap<Any, WeakReference<View>>()
        val main = Handler(Looper.getMainLooper())
        val inAdapter = ThreadLocal<Boolean>()
        val inPromotionNotification = ThreadLocal<Boolean>()
        // 扫描晚于页面创建时刷新已有列表；只记弱引用，每个 Adapter 仅回放一次。
        ActivePageObserver.observeFragment(callbacks, home) { root ->
            val list = context.views.findByResourceName(root, context.application.packageName, "function_set_view")
                ?: return@observeFragment
            if (!getAdapter.declaringClass.isInstance(list) || computing.invoke(list) == true) return@observeFragment
            val adapter = getAdapter.invoke(list) ?: return@observeFragment
            if (!adapterType.isInstance(adapter) || refreshed.containsKey(adapter)) return@observeFragment
            refresh.invoke(adapter)
            refreshed[adapter] = WeakReference(list)
        }
        callbacks.onClose {
            // 组失效后恢复宿主列表；排队任务不持有页面，也不会重新激活屏蔽。
            val restore = {
                refreshed.entries.toList().forEach { (adapter, view) ->
                    val list = view.get()
                    if (list?.isAttachedToWindow == true && computing.invoke(list) == false &&
                        getAdapter.invoke(list) === adapter) refresh.invoke(adapter)
                }
                refreshed.clear()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) restore() else main.post { restore() }
        }
        context.hooks.hook(gate).setId("health.page:promotion:visible")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain -> if (inAdapter.get() == true) false else chain.proceed() }
        scopes.forEachIndexed { index, method ->
            context.hooks.hook(method).setId("health.page:promotion:scope:$index")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    within(inAdapter) { chain.proceed() }
                }
        }
        context.hooks.hook(notification).setId("health.page:promotion:notification")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                within(inPromotionNotification) { chain.proceed() }
            }
        itemNotifications.forEachIndexed { index, method ->
            context.hooks.hook(method).setId("health.page:promotion:item-notification:$index")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    // 普通卡片数量没有变化；仅略过推广消息链中本列表的插入/移除通知。
                    if (inPromotionNotification.get() == true && adapterType.isInstance(chain.thisObject)) null
                    else chain.proceed()
                }
        }
        return scopes.size + 4
    }

    private inline fun <T> within(flag: ThreadLocal<Boolean>, action: () -> T): T {
        val previous = flag.get()
        flag.set(true)
        return try { action() } finally {
            if (previous == null) flag.remove() else flag.set(previous)
        }
    }
}
