package love.nairain.huawei.hook.feature

import android.view.View
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.resolver.BadgeTargets
import love.nairain.huawei.hook.util.ActivePageObserver
import love.nairain.huawei.hook.util.BadgeViews
import love.nairain.huawei.hook.util.applyPage
import java.lang.reflect.Method
import java.util.WeakHashMap

/** 默认关闭。顶部和列表只消费各自完整定位证据，失败时独立保留宿主显示。 */
internal object MineBadgeHooks {
    fun installHeader(context: HookContext): Int = install(context, header = true)
    fun installRows(context: HookContext): Int = install(context, header = false)

    private fun install(context: HookContext, header: Boolean): Int {
        if (context.config[SettingsKeys.MINE_BADGES] != true) return 0
        val fragment = requireNotNull(context.targets.type(context.classLoader, context.points.mineFragment))
        val create = requireNotNull(context.targets.method(fragment, "onCreateView", 3))
        val resume = requireNotNull(context.targets.method(fragment, "onResume", 0, Void.TYPE))
        val scope = context.hooks.callbacks()
        val badges = BadgeViews(scope, context.views,
            if (header) BadgeTargets.HEADER_RESOURCES else BadgeTargets.ROW_RESOURCES)
        val pages = WeakHashMap<View, Unit>()
        fun apply(root: View?) {
            scope.applyPage(root) { page -> pages[page] = Unit; badges.hide(page) }
        }
        fun belongsToPage(view: View): Boolean = generateSequence(view as View?) { it.parent as? View }
            .any { pages.containsKey(it) }
        val source = if (header) "mine.badges.header" else "mine.badges.rows"
        var count = 0
        fun after(method: Method, root: (Any, List<Any>, Any?) -> View?, binding: Boolean = false) {
            context.hooks.hook(method).setId("$source:${method.name}")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (binding) scope.run { badges.beforeUpdate(root(chain.thisObject, chain.args, null)) }
                    val result = chain.proceed()
                    scope.run {
                        val view = root(chain.thisObject, chain.args, result)
                        if (binding) badges.hide(view) else apply(view)
                    }
                    result
                }
            count++
        }
        after(create, { instance, _, result -> (result as? View)?.takeIf { fragment.isInstance(instance) } })
        after(resume, { instance, _, _ ->
            if (fragment.isInstance(instance)) context.targets.invokeNoArgs(instance, "getView") as? View else null
        })
        if (header) {
            after(requireNotNull(context.targets.method(fragment, "badges.messages", 1, Void.TYPE)),
                { instance, _, _ ->
                    if (fragment.isInstance(instance)) context.targets.invokeNoArgs(instance, "getView") as? View else null
                }, binding = true)
            val head = requireNotNull(context.targets.type(context.classLoader, BadgeTargets.MINE_HEAD))
            after(requireNotNull(context.targets.method(head, "badges.rank", 2, Void.TYPE)),
                { instance, _, _ -> (instance as? View)?.takeIf(::belongsToPage) }, binding = true)
        } else {
            val adapter = requireNotNull(context.targets.type(context.classLoader, BadgeTargets.MINE_ADAPTER))
            after(requireNotNull(context.targets.method(adapter, "badges.bind", 2, Void.TYPE)),
                { _, args, _ -> args.firstOrNull()?.let { context.targets.fieldValue(it, "badges.root") } as? View },
                binding = true)
        }
        ActivePageObserver.observeFragment(scope, fragment, ::apply)
        return count
    }
}
