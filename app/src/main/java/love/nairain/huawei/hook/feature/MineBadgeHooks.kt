package love.nairain.huawei.hook.feature

import android.os.Looper
import android.view.View
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.resolver.BadgeTargets
import love.nairain.huawei.hook.util.ActivePageObserver
import love.nairain.huawei.hook.util.BadgeViews
import love.nairain.huawei.hook.util.applyPage
import love.nairain.huawei.hook.util.post
import java.lang.reflect.Method
import java.util.Locale
import java.util.WeakHashMap

/** 默认关闭。每种角标只消费本组完整证据，失败时独立保留宿主显示。 */
internal object MineBadgeHooks {
    private enum class Area { HEADER, ROWS, GRID, KAKA }

    fun installHeader(context: HookContext): Int = install(context, Area.HEADER)
    fun installRows(context: HookContext): Int = install(context, Area.ROWS)
    fun installGrid(context: HookContext): Int = install(context, Area.GRID)
    fun installKaka(context: HookContext): Int = install(context, Area.KAKA)

    private fun install(context: HookContext, area: Area): Int {
        if (context.config[SettingsKeys.MINE_BADGES] != true) return 0
        val fragment = requireNotNull(context.targets.type(context.classLoader, context.points.mineFragment))
        val create = requireNotNull(context.targets.method(fragment, "onCreateView", 3))
        val resume = requireNotNull(context.targets.method(fragment, "onResume", 0, Void.TYPE))
        val scope = context.hooks.callbacks()
        val resources = when (area) {
            Area.HEADER -> BadgeTargets.HEADER_RESOURCES
            Area.ROWS -> BadgeTargets.ROW_RESOURCES
            Area.GRID -> BadgeTargets.GRID_RESOURCES
            Area.KAKA -> BadgeTargets.KAKA_RESOURCES
        }
        val badges = BadgeViews(scope, context.views, resources)
        val pages = WeakHashMap<View, Unit>()
        fun apply(root: View?) {
            scope.applyPage(root) { page -> pages[page] = Unit; badges.hide(page) }
        }
        fun belongsToPage(view: View): Boolean = generateSequence(view as View?) { it.parent as? View }
            .any { pages.containsKey(it) }
        val source = "mine.badges.${area.name.lowercase(Locale.ROOT)}"
        var count = 0
        fun after(method: Method, root: (Any, List<Any>, Any?) -> View?, binding: Boolean = false,
                  pageOnly: Boolean = false) {
            context.hooks.hook(method).setId("$source:${method.toGenericString()}")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    // 新路径只在主线程访问页面与 holder；异常线程调用保持宿主行为，页面观察器负责回放。
                    if (pageOnly && Looper.myLooper() != Looper.getMainLooper()) return@intercept chain.proceed()
                    if (binding) scope.run {
                        // 脱离页面的复用项也须恢复已有快照，再记录本次宿主绑定结果。
                        // beforeUpdate 只恢复本组曾隐藏的 View，不会改动其他页面的新实例。
                        badges.beforeUpdate(root(chain.thisObject, chain.args, null))
                    }
                    val result = chain.proceed()
                    scope.run {
                        val view = root(chain.thisObject, chain.args, result)
                        if (!binding) apply(view)
                        else if (view != null && pageOnly) {
                            if (belongsToPage(view)) badges.hide(view)
                            // RecyclerView 初次绑定可早于挂载，只在挂回已核验页面后处理。
                            else scope.post(view) { if (belongsToPage(it)) badges.hide(it) }
                        } else badges.hide(view)
                    }
                    result
                }
            count++
        }
        after(create, { instance, _, result -> (result as? View)?.takeIf { fragment.isInstance(instance) } })
        after(resume, { instance, _, _ ->
            if (fragment.isInstance(instance)) context.targets.invokeNoArgs(instance, "getView") as? View else null
        })
        when (area) {
            Area.HEADER -> {
                after(requireNotNull(context.targets.method(fragment, "badges.messages", 1, Void.TYPE)),
                    { instance, _, _ ->
                        if (fragment.isInstance(instance)) context.targets.invokeNoArgs(instance, "getView") as? View else null
                    }, binding = true)
                val head = requireNotNull(context.targets.type(context.classLoader, BadgeTargets.MINE_HEAD))
                after(requireNotNull(context.targets.method(head, "badges.rank", 2, Void.TYPE)),
                    { instance, _, _ -> (instance as? View)?.takeIf(::belongsToPage) }, binding = true)
            }
            Area.ROWS -> {
                val adapter = requireNotNull(context.targets.type(context.classLoader, BadgeTargets.MINE_ADAPTER))
                after(requireNotNull(context.targets.method(adapter, "badges.bind", 2, Void.TYPE)),
                    { _, args, _ -> args.firstOrNull()?.let { context.targets.fieldValue(it, "badges.root") } as? View },
                    binding = true)
            }
            Area.GRID -> {
                val holder = requireNotNull(context.targets.type(context.classLoader, BadgeTargets.MINE_GRID_HOLDER))
                val adapter = requireNotNull(context.targets.type(context.classLoader, context.points.mineGridAdapter))
                fun root(instance: Any): View? {
                    if (!holder.isInstance(instance)) return null
                    val view = context.targets.fieldValue(instance, "badges.root") as? View ?: return null
                    val expected = BadgeTargets.GRID_RESOURCES.map { name ->
                        context.views.findByResourceName(view, context.application.packageName, name) ?: return null
                    }.toSet()
                    val actual = (0..1).map { context.targets.fieldValue(instance, "badges.grid.view.$it") }.toSet()
                    return view.takeIf { actual == expected }
                }
                after(requireNotNull(context.targets.method(adapter, "badges.grid.bind", 2, Void.TYPE)),
                    { _, args, _ -> args.firstOrNull()?.let(::root) }, binding = true, pageOnly = true)
                after(requireNotNull(context.targets.method(holder, "badges.grid.init", 0, Void.TYPE)),
                    { instance, _, _ -> root(instance) }, binding = true, pageOnly = true)
                after(requireNotNull(context.targets.method(holder, "badges.grid.update", 1, Void.TYPE)),
                    { instance, _, _ -> root(instance) }, binding = true, pageOnly = true)
            }
            Area.KAKA -> {
                val head = requireNotNull(context.targets.type(context.classLoader, BadgeTargets.MINE_HEAD))
                after(requireNotNull(context.targets.method(head, "badges.kaka.update", 1, Void.TYPE)),
                    { instance, _, _ ->
                        (instance as? View)?.takeIf { head.isInstance(instance) }?.takeIf { view ->
                            val badge = context.views.findByResourceName(view, context.application.packageName,
                                "kaka_to_be_collected_text")
                            badge != null && context.targets.fieldValue(instance, "badges.kaka.view") === badge
                        }
                    }, binding = true, pageOnly = true)
            }
        }
        ActivePageObserver.observeFragment(scope, fragment, ::apply)
        return count
    }
}
