package love.nairain.huawei.hook.feature

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.HookFeature
import love.nairain.huawei.hook.InstallResult
import love.nairain.huawei.hook.installIsolated
import love.nairain.huawei.hook.resolver.ListFilters
import love.nairain.huawei.hook.resolver.ReflectionTargets
import love.nairain.huawei.hook.resolver.SportContentKeyResolver
import love.nairain.huawei.hook.resolver.SportPageTargets
import love.nairain.huawei.hook.resolver.SportTab
import love.nairain.huawei.hook.util.ActivePageObserver
import love.nairain.huawei.hook.util.applyPage
import love.nairain.huawei.hook.util.post
import love.nairain.huawei.hook.util.ViewSelectors
import love.nairain.huawei.hook.util.ViewTrimmer
import java.util.WeakHashMap

class SportPageFeature : HookFeature {
    override val id = "sport.page"
    private val originalTranslations = WeakHashMap<View, Float>()

    override fun install(context: HookContext): InstallResult {
        if (!SettingsCatalog.hasHidden(SettingsCategory.SPORT, context.config)) {
            return InstallResult.Disabled
        }
        var count = 0
        count += context.installIsolated("sport.top") { installTopControls(context) }
        count += context.installIsolated("sport.sections") { installSections(context) }
        count += context.installIsolated("sport.quick-entry-bind") { installQuickEntryBinding(context) }
        return if (count > 0) InstallResult.Installed(count)
        else InstallResult.Unsupported("verified sport symbols unavailable")
    }

    private fun installTopControls(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader, context.points.sportFragment) ?: return 0
        val method = ReflectionTargets.method(type, "onCreateView", 3) ?: return 0
        val callbacks = context.hooks.callbacks()
        ActivePageObserver.observeFragment(callbacks, type) { view -> applyPageViews(view, context) }
        context.hooks.hook(method).setId("$id:top")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                callbacks.applyPage(result as? View) { view -> applyPageViews(view, context) }
                result
            }
        var count = 1
        ReflectionTargets.method(type, "onResume", 0)?.let { resume ->
            context.hooks.hook(resume).setId("$id:resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    callbacks.applyPage(ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View) {
                        view -> applyPageViews(view, context)
                    }
                    result
                }
            count++
        }
        ReflectionTargets.method(type, "setUserVisibleHint", 1, Void.TYPE)?.let { visibility ->
            context.hooks.hook(visibility).setId("$id:visibility")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    callbacks.applyPage(ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View) {
                        view -> applyPageViews(view, context)
                    }
                    result
                }
            count++
        }
        return count
    }

    private fun applyPageViews(root: View, context: HookContext) {
        val page = selectedTab(root, context.application.packageName)
        val pageControls = when (page) {
            SportTab.RUN -> setOf(SettingsKeys.SPORT_RUN_SUMMARY, SettingsKeys.SPORT_RUN_ROUTE,
                SettingsKeys.SPORT_RUN_WARMUP, SettingsKeys.SPORT_RUN_BEGIN, SettingsKeys.SPORT_RUN_MUSIC)
            SportTab.YOGA -> setOf(SettingsKeys.SPORT_YOGA_COURSES)
            SportTab.FITNESS -> setOf(SettingsKeys.SPORT_FITNESS_SUMMARY)
            else -> emptySet()
        }
        ViewSelectors.applyByResourceNames(
            root,
            context.application.packageName,
            SportPageTargets.controls.filterValues { key -> key !in PAGE_CONTROL_KEYS || key in pageControls },
            context.config,
        )
        if (!love.nairain.huawei.hook.HookInstallPolicy.acceptsVersion(context.versionName, context.versionCode)) return
        applyQuickEntryGroup(root, context)
        if (page == SportTab.PLAN) {
            ViewSelectors.findByResourceName(root, context.application.packageName, "plan_resource_slot")?.let { slot ->
                if (context.config[SettingsKeys.SPORT_PLAN_CARDS] == true) ViewTrimmer.collapse(slot)
                else ViewTrimmer.restore(slot)
            }
        }
        if (page == SportTab.YOGA) {
            ViewSelectors.findByResourceName(root, context.application.packageName, "normal_view_fitness")?.let { summary ->
                if (context.config[SettingsKeys.SPORT_YOGA_SUMMARY] == true) ViewTrimmer.collapse(summary)
                else ViewTrimmer.restore(summary)
            }
        }
        if (context.config[SettingsKeys.SPORT_QUICK_ENTRIES] != true) {
            ViewSelectors.collapseContainersByText(
                root,
                SportPageTargets.quickEntries,
                setOf("item_quick_entry_root_layout"),
                context.config,
            )
        }
        ViewSelectors.applyTitledContainers(
            root,
            SportPageTargets.sections.filterValues { key ->
                when (page) {
                    SportTab.RECOMMEND -> key in RECOMMEND_SECTIONS
                    SportTab.PLAN -> key in PLAN_SECTIONS
                    SportTab.RUN -> key == SettingsKeys.SPORT_RUN_TRAINING
                    SportTab.FITNESS -> key in FITNESS_SECTIONS
                    else -> false
                }
            },
            setOf("series_course_layout", "layout_marketing_grid", "section_root_view", "item_two_landscape_layout"),
            context.config,
        )
        if (page == SportTab.PLAN) alignPlanCards(root, context)
    }

    private fun alignPlanCards(root: View, context: HookContext) {
        val slot = ViewSelectors.findByResourceName(root, context.application.packageName,
            "plan_resource_slot") ?: return
        val rows = mutableMapOf<String, View>()
        ViewSelectors.walk(slot) { view ->
            if (ViewSelectors.resourceEntryName(view) != "item_two_landscape_layout") return@walk
            originalTranslations.remove(view)?.let { view.translationX = it }
            ViewSelectors.text(view)?.let { title -> rows[title] = view }
        }
        val weight = rows["智能体重管理"] ?: return
        val training = rows["智能训练计划"] ?: return
        val grid = training.parent as? ViewGroup ?: return
        if (weight.parent !== grid) return
        var section: View? = grid
        while (section != null && ViewSelectors.resourceEntryName(section) != "layout_marketing_grid") {
            section = section.parent as? View
        }
        section?.let {
            val hideBoth = rows.size == 2 &&
                context.config[SettingsKeys.SPORT_PLAN_WEIGHT] == true &&
                context.config[SettingsKeys.SPORT_PLAN_TRAINING] == true
            if (hideBoth) ViewTrimmer.collapse(it) else ViewTrimmer.restore(it)
        }
        if (context.config[SettingsKeys.SPORT_PLAN_WEIGHT] == true &&
            context.config[SettingsKeys.SPORT_PLAN_TRAINING] != true) {
            val base = training.translationX
            originalTranslations[training] = base
            training.translationX = base + grid.paddingLeft - training.left
        }
    }

    private fun selectedTab(root: View, packageName: String): SportTab? {
        val bar = ViewSelectors.findByResourceName(root, packageName, "track_sport_tab") ?: return null
        var selected: SportTab? = null
        ViewSelectors.walk(bar) { view ->
            val label = view as? TextView ?: return@walk
            if (label.isSelected) selected = SportPageTargets.tabs[label.text?.toString()]
        }
        return selected
    }

    private fun applyQuickEntryGroup(root: View, context: HookContext) {
        ViewSelectors.walk(root) { view ->
            if (ViewSelectors.resourceEntryName(view) != "layout_marketing_grid") return@walk
            if (ViewSelectors.findByResourceName(view, context.application.packageName, "item_quick_entry_root_layout") == null) return@walk
            if (context.config[SettingsKeys.SPORT_QUICK_ENTRIES] == true) ViewTrimmer.collapse(view)
            else ViewTrimmer.restore(view)
        }
    }

    private fun installSections(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader, context.points.sportTrigger) ?: return 0
        val method = ReflectionTargets.method(type, "setCacheBeansList", 1) ?: return 0
        context.hooks.hook(method).setId("$id:sections")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val resPosId = ReflectionTargets.invokeNoArgs(chain.thisObject, "getResPosId") as? Int
                val args = chain.args.toTypedArray()
                if (resPosId == SPORT_RES_POS_ID) {
                    val source = chain.args.firstOrNull() as? List<*>
                    if (source != null) {
                        args[0] = ListFilters.copyAndFilter(source.filterNotNull(), { section ->
                            SportPageTargets.effectiveQuickEntryKey(sectionKey(section, context), context.config)
                        }, context.config)
                    }
                }
                chain.proceed(args)
            }
        return 1
    }

    private fun installQuickEntryBinding(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader, context.points.sportColumnAdapter) ?: return 0
        val method = ReflectionTargets.methods(type, "x", 2).firstOrNull {
            it.returnType == Void.TYPE &&
                it.parameterTypes.getOrNull(0)?.name == "${context.points.sportColumnAdapter}\$b" &&
                it.parameterTypes.getOrNull(1) == Int::class.javaPrimitiveType
        } ?: return 0
        val callbacks = context.hooks.callbacks()
        context.hooks.hook(method).setId("$id:quick-entry-bind")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val holder = chain.args.firstOrNull()
                val root = holder?.let { ReflectionTargets.fieldValue(it, "cq") } as? View
                callbacks.run { applyBoundQuickEntry(root, context) }
                root?.let { callbacks.post(it) { view -> applyBoundQuickEntry(view, context) } }
                result
            }
        return 1
    }

    private fun applyBoundQuickEntry(root: View?, context: HookContext) {
        if (!ViewSelectors.hasAncestorResourceName(root, "sport_viewPager_container")) return
        val key = SportPageTargets.effectiveQuickEntryKey(
            SportPageTargets.quickEntries[ViewSelectors.text(root)], context.config)
        if (key != null && context.config[key] == true) {
            root?.let(ViewTrimmer::collapse)
        } else {
            root?.let(ViewTrimmer::restore)
        }
    }

    private fun sectionKey(section: Any, context: HookContext): String? {
        val info = ReflectionTargets.invokeNoArgs(section, "n")
        val resourceName = listOfNotNull(
            ReflectionTargets.invokeNoArgs(info ?: return null, "getResourceId") as? String,
            ReflectionTargets.invokeNoArgs(info, "getResourceName") as? String,
        ).joinToString("|")
        val provider = ReflectionTargets.invokeNoArgs(section, "c")?.javaClass?.name
        val title = if (love.nairain.huawei.hook.HookInstallPolicy.acceptsVersion(context.versionName, context.versionCode))
            ViewSelectors.text(ReflectionTargets.invokeNoArgs(section, "q") as? View) else null
        return SportContentKeyResolver.resolve(resourceName, provider, title)
    }

    private companion object {
        const val SPORT_RES_POS_ID = 4040
        val PAGE_CONTROL_KEYS = setOf(SettingsKeys.SPORT_RUN_SUMMARY, SettingsKeys.SPORT_RUN_ROUTE,
            SettingsKeys.SPORT_RUN_WARMUP, SettingsKeys.SPORT_RUN_BEGIN, SettingsKeys.SPORT_RUN_MUSIC,
            SettingsKeys.SPORT_YOGA_COURSES, SettingsKeys.SPORT_FITNESS_SUMMARY)
        val RECOMMEND_SECTIONS = setOf(SettingsKeys.SPORT_ENJOY, SettingsKeys.SPORT_TODAY,
            SettingsKeys.SPORT_MORE_COURSES, SettingsKeys.SPORT_COACHES, SettingsKeys.SPORT_LATEST)
        val PLAN_SECTIONS = setOf(SettingsKeys.SPORT_PLAN_WEIGHT, SettingsKeys.SPORT_PLAN_TRAINING)
        val FITNESS_SECTIONS = setOf(SettingsKeys.SPORT_MY_COURSES, SettingsKeys.SPORT_WEEKLY_PLAN)
    }
}
