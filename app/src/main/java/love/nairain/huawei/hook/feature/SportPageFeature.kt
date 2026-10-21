package love.nairain.huawei.hook.feature

import android.view.View
import android.view.ViewGroup
import android.os.Looper
import android.os.Handler
import java.lang.ref.WeakReference
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.config.SportTabPolicy
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.HookFeature
import love.nairain.huawei.hook.InstallResult
import love.nairain.huawei.hook.installSource
import love.nairain.huawei.hook.resolver.SportPageTargets
import love.nairain.huawei.hook.util.ActivePageObserver
import love.nairain.huawei.hook.util.applyPage
import love.nairain.huawei.hook.util.post
import love.nairain.huawei.hook.util.ViewTrimmer
import java.util.WeakHashMap

class SportPageFeature : HookFeature {
    override val id = "sport.page"
    private val originalTranslations = WeakHashMap<View, Float>()
    private val runtime = SportPageRuntime()
    private val main = Handler(Looper.getMainLooper())
    private val fixedViews = WeakHashMap<View, Boolean>()
    private val contents = SportContentViews()
    private var contentActive = false
    private var quickActive = false
    private val alignedViews = WeakHashMap<View, Boolean>()

    override fun install(context: HookContext): InstallResult {
        if (context.config[SettingsKeys.ENABLED] != true) {
            return InstallResult.Disabled
        }
        var count = 0
        count += context.installSource("sport.identity", ::installIdentity)
        count += context.installSource("sport.navigation", ::installNavigation)
        count += context.installSource("sport.fixed", ::installContent)
        count += context.installSource("sport.top", ::installTopControls)
        // 区块仅修改显示层，不再从宿主业务缓存移除数据。
        count += context.installSource("sport.quick-entry-bind", ::installQuickEntryBinding)
        return if (count > 0) InstallResult.Installed(count)
        else InstallResult.Unsupported("verified sport symbols unavailable")
    }

    private fun installIdentity(context: HookContext): Int {
        val method = context.targets.methodForRole("sport.tabs") ?: return 0
        val callbacks = context.hooks.callbacks()
        val replayed = WeakHashMap<Any, Boolean>()
        ActivePageObserver.observe(callbacks) { activity ->
            love.nairain.huawei.hook.resolver.ActiveFragmentTargets.forEach(activity, method.declaringClass) { fragment ->
                if (replayed[fragment] != true) {
                    val source = context.targets.fieldValue(fragment, "sport.tabs.current") as? List<*>
                    if (!source.isNullOrEmpty()) {
                        replayed[fragment] = true
                        method.invoke(fragment, ArrayList(source))
                    }
                }
            }
        }
        context.hooks.hook(method).setId("$id:tabs").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val source = chain.args.firstOrNull() as? List<*>
                val args = chain.args.toTypedArray()
                if (source != null && source.none { it == null }) {
                    val filtered = runtime.tabs(source.filterNotNull(), requireNotNull(chain.thisObject), context)
                    if (runtime.navigationActive && Looper.myLooper() == Looper.getMainLooper()) {
                        val owner = chain.thisObject
                        val current = owner?.let { context.targets.fieldValue(it, "sport.current") } as? Int
                        if (owner != null && current != null) SportTabPolicy.target(current, filtered.mapNotNull { runtime.id(it, context) })
                            ?.let { context.targets.setFieldValue(owner, "sport.current", it) }
                    }
                    args[0] = ArrayList(filtered)
                }
                chain.proceed(args)
            }
        return 1
    }

    private fun installNavigation(context: HookContext): Int {
        val add = context.targets.methodForRole("sport.add") ?: return 0
        val remove = context.targets.methodForRole("sport.remove") ?: return 0
        val list = context.targets.methodForRole("sport.tabs") ?: return 0
        val order = context.targets.methodForRole("sport.order") ?: return 0
        val jump = context.targets.methodForRole("sport.jump") ?: return 0
        val callbacks = context.hooks.callbacks()
        callbacks.afterActivation { main.post { callbacks.run { runtime.navigationActive = true; runtime.replayNavigation(context) } } }
        callbacks.onClose {
            if (Looper.myLooper() == Looper.getMainLooper()) runtime.restoreNavigation(context)
            else main.post { runtime.restoreNavigation(context) }
        }
        listOf(add to "sport.add", remove to "sport.remove").forEach { (method, role) ->
            context.hooks.hook(method).setId("$id:$role").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val bean = chain.thisObject?.let { context.targets.fieldValue(it, "$role.bean") }
                    val owner = chain.thisObject?.let { context.targets.fieldValue(it, "$role.owner") }
                    val full = if (bean != null && owner != null) runtime.changed(bean, role == "sport.remove", owner, context) else null
                    if (full != null && owner != null && callbacks.isActive && Looper.myLooper() == Looper.getMainLooper()) {
                        list.invoke(owner, ArrayList(full))
                        null
                    } else chain.proceed()
                }
        }
        context.hooks.hook(order).setId("$id:order").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val source = chain.args.firstOrNull() as? List<*>
                val args = chain.args.toTypedArray()
                if (source != null && source.none { it == null } && callbacks.isActive) {
                    val values = runtime.ordered(source.filterNotNull(), requireNotNull(chain.thisObject), context)
                    args[0] = ArrayList(values)
                    val owner = chain.thisObject
                    val selected = owner?.let { context.targets.fieldValue(it, "sport.current") } as? Int
                    if (owner != null && selected != null) SportTabPolicy.target(selected, values.mapNotNull { runtime.id(it, context) })
                        ?.let { context.targets.setFieldValue(owner, "sport.current", it) }
                }
                chain.proceed(args)
            }
        context.hooks.hook(jump).setId("$id:jump").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val args = chain.args.toTypedArray()
                if (callbacks.isActive) chain.thisObject?.let { owner ->
                    (args.getOrNull(1) as? Int)?.let { requested ->
                        SportTabPolicy.target(requested, runtime.available(owner, context))?.let { args[1] = it }
                    }
                }
                chain.proceed(args)
            }
        return 4
    }

    private fun installContent(context: HookContext): Int {
        val create = context.targets.methodForRole("getPageResTrigger")?.declaringClass?.let {
            context.targets.method(it, "onCreateView", 3)
        } ?: return 0
        val sections = context.targets.type(context.classLoader, context.points.sportTrigger)?.let {
            context.targets.method(it, "setCacheBeansList", 1)
        } ?: return 0
        val callbacks = context.hooks.callbacks()
        callbacks.afterActivation { main.post { callbacks.run { contentActive = true } } }
        callbacks.onClose {
            val restore = {
                contentActive = false; contents.restore()
                alignedViews.keys.toList().forEach(ViewTrimmer::restore); alignedViews.clear()
                originalTranslations.entries.toList().forEach { (view, x) -> view.translationX = x }; originalTranslations.clear()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) restore() else main.post { restore() }
        }
        context.hooks.hook(create).setId("$id:child-root").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                var page: Int? = null
                callbacks.run { page = chain.thisObject?.let { runtime.pageCreated(it, result as? View, context) } }
                if (page != null) callbacks.applyPage(result as? View) { view ->
                    runtime.pageOf(view, context)?.let { page -> applyContentViews(view, page, context) }
                }
                result
            }
        context.hooks.hook(sections).setId("$id:sections").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val trigger = WeakReference(chain.thisObject)
                val records = (chain.args.firstOrNull() as? List<*>)?.filterNotNull()?.map(::WeakReference)
                if (records != null) {
                    var unregister: () -> Unit = {}
                    val task = Runnable {
                        unregister()
                        callbacks.run { trigger.get()?.let {
                            runtime.sectionList(it, records.mapNotNull { ref -> ref.get() }, context).forEach { (root, page) ->
                                callbacks.applyPage(root) { applyContentViews(it, page, context) }
                            }
                        } }
                    }
                    unregister = callbacks.onClose { main.removeCallbacks(task) }
                    main.post(task)
                }
                result
            }
        return 2
    }

    private fun installTopControls(context: HookContext): Int {
        val type = context.targets.type(context.classLoader, context.points.sportFragment) ?: return 0
        val method = context.targets.method(type, "onCreateView", 3) ?: return 0
        val callbacks = context.hooks.callbacks()
        callbacks.onClose {
            val restore = {
                fixedViews.keys.toList().forEach(ViewTrimmer::restore)
                fixedViews.clear()
                originalTranslations.entries.toList().forEach { (view, x) -> view.translationX = x }
                originalTranslations.clear()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) restore() else main.post { restore() }
        }
        ActivePageObserver.observeFragment(callbacks, type) { view -> applyPageViews(view, context) }
        context.hooks.hook(method).setId("$id:top")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                callbacks.applyPage(result as? View) { view -> applyPageViews(view, context) }
                result
            }
        var count = 1
        context.targets.method(type, "onResume", 0)?.let { resume ->
            context.hooks.hook(resume).setId("$id:resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    callbacks.applyPage(context.targets.invokeNoArgs(chain.thisObject, "getView") as? View) {
                        view -> applyPageViews(view, context)
                    }
                    result
                }
            count++
        }
        context.targets.method(type, "setUserVisibleHint", 1, Void.TYPE)?.let { visibility ->
            context.hooks.hook(visibility).setId("$id:visibility")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    callbacks.applyPage(context.targets.invokeNoArgs(chain.thisObject, "getView") as? View) {
                        view -> applyPageViews(view, context)
                    }
                    result
                }
            count++
        }
        return count
    }

    private fun applyPageViews(root: View, context: HookContext) {
        context.views.walk(root) { view ->
            if (context.views.resourceEntryName(view) in setOf("track_sport_tab", "sport_search_icon", "sport_global_search_view", "more_and_red_point")) fixedViews[view] = true
        }
        context.views.applyByResourceNames(root, context.application.packageName,
            SportPageTargets.controls.filterValues { it in setOf(SettingsKeys.SPORT_CATEGORY_BAR, SettingsKeys.SPORT_SEARCH, SettingsKeys.SPORT_MORE) }, context.config)
        runtime.rootsUnder(root, context).forEach { (view, page) -> applyContentViews(view, page, context) }
    }

    private fun applyContentViews(root: View, pageId: Int, source: HookContext) {
        if (!contentActive) return
        contents.apply(root, pageId, runtime.sections(pageId, source), source.views, source.sport, source.sportLayout, entriesEnabled = quickActive)
        if (pageId == 2) alignPlanCards(root, source)
    }

    private fun alignPlanCards(root: View, context: HookContext) {
        val slot = context.views.findByResourceName(root, context.application.packageName,
            "plan_resource_slot") ?: return
        val rows = mutableMapOf<String, View>()
        context.views.walk(slot) { view ->
            if (context.views.resourceEntryName(view) != "item_two_landscape_layout") return@walk
            originalTranslations.remove(view)?.let { view.translationX = it }
            context.views.text(view)?.let { title -> rows[title] = view }
        }
        val weight = rows["智能体重管理"] ?: return
        val training = rows["智能训练计划"] ?: return
        val grid = training.parent as? ViewGroup ?: return
        if (weight.parent !== grid) return
        var section: View? = grid
        while (section != null && context.views.resourceEntryName(section) != "layout_marketing_grid") {
            section = section.parent as? View
        }
        section?.let {
            alignedViews[it] = true
            val hideBoth = rows.size == 2 &&
                context.sport.hidden(love.nairain.huawei.config.SportItem.fixed(2, SettingsKeys.SPORT_PLAN_WEIGHT), context.sportLayout) == true &&
                context.sport.hidden(love.nairain.huawei.config.SportItem.fixed(2, SettingsKeys.SPORT_PLAN_TRAINING), context.sportLayout) == true
            if (hideBoth) ViewTrimmer.collapse(it) else ViewTrimmer.restore(it)
        }
        if (context.sport.hidden(love.nairain.huawei.config.SportItem.fixed(2, SettingsKeys.SPORT_PLAN_WEIGHT), context.sportLayout) == true &&
            context.sport.hidden(love.nairain.huawei.config.SportItem.fixed(2, SettingsKeys.SPORT_PLAN_TRAINING), context.sportLayout) != true) {
            val base = training.translationX
            originalTranslations[training] = base
            training.translationX = base + grid.paddingLeft - training.left
        }
    }

    private fun installQuickEntryBinding(context: HookContext): Int {
        val type = context.targets.type(context.classLoader, context.points.sportColumnAdapter) ?: return 0
        val method = context.targets.method(type, "quick.bind", 2) ?: return 0
        val callbacks = context.hooks.callbacks()
        callbacks.afterActivation { main.post { callbacks.run { quickActive = true } } }
        callbacks.onClose {
            val restore = { quickActive = false; contents.restoreEntries() }
            if (Looper.myLooper() == Looper.getMainLooper()) restore() else main.post { restore() }
        }
        context.hooks.hook(method).setId("$id:quick-entry-bind")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val holder = chain.args.firstOrNull()
                val root = holder?.let { context.targets.fieldValue(it, "quick.root") } as? View
                // 恢复上一条目快照后再让宿主绑定，避免把上一项的零尺寸带给下一项。
                callbacks.run { root?.let(contents::beforeBind) }
                val result = chain.proceed()
                fun apply(view: View) {
                    runtime.pageOf(view, context)?.let { page ->
                        // 复用后整页重新核对；不会把一个 holder 的标题用于其他预加载页。
                        runtime.rootsUnderPage(page).forEach { applyContentViews(it, page, context) }
                    }
                }
                callbacks.run { root?.let(::apply) }
                root?.let { callbacks.post(it, ::apply) }
                result
            }
        return 1
    }

}
