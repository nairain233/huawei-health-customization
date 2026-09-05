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
import love.nairain.huawei.hook.resolver.ListFilters
import love.nairain.huawei.hook.resolver.ReflectionTargets
import love.nairain.huawei.hook.resolver.SportContentKeyResolver
import love.nairain.huawei.hook.util.ViewSelectors
import love.nairain.huawei.hook.util.ViewTrimmer
import java.util.Collections
import java.util.WeakHashMap

class SportPageFeature : HookFeature {
    override val id = "sport.page"

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
        context.framework.hook(method).setId("$id:top")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                (result as? View)?.let {
                    observeLayouts(it, context)
                    applyPageViews(it, context)
                    it.post { applyPageViews(it, context) }
                }
                result
            }
        var count = 1
        ReflectionTargets.method(type, "onResume", 0)?.let { resume ->
            context.framework.hook(resume).setId("$id:resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    (ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View)?.let {
                        observeLayouts(it, context)
                        applyPageViews(it, context)
                        it.post { applyPageViews(it, context) }
                    }
                    result
                }
            count++
        }
        ReflectionTargets.method(type, "setUserVisibleHint", 1, Void.TYPE)?.let { visibility ->
            context.framework.hook(visibility).setId("$id:visibility")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    (ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View)?.let {
                        observeLayouts(it, context)
                        applyPageViews(it, context)
                        it.post { applyPageViews(it, context) }
                    }
                    result
                }
            count++
        }
        return count
    }

    private fun applyPageViews(root: View, context: HookContext) {
        ViewSelectors.applyByResourceNames(
            root,
            context.application.packageName,
            TOP_VIEWS,
            context.config,
        )
        ViewSelectors.collapseContainersByText(
            root,
            QUICK_ENTRY_TITLES,
            setOf("item_quick_entry_root_layout"),
            context.config,
        )
        ViewSelectors.collapseContainersByText(
            root,
            SECTION_TITLES,
            setOf("series_course_layout", "layout_marketing_grid"),
            context.config,
        )
    }

    private fun observeLayouts(root: View, context: HookContext) {
        synchronized(layoutListeners) {
            if (layoutListeners.containsKey(root)) return
            val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                applyPageViews(view, context)
            }
            layoutListeners[root] = listener
            root.addOnLayoutChangeListener(listener)
        }
    }

    private fun installSections(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader, context.points.sportTrigger) ?: return 0
        val method = ReflectionTargets.method(type, "setCacheBeansList", 1) ?: return 0
        context.framework.hook(method).setId("$id:sections")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val resPosId = ReflectionTargets.invokeNoArgs(chain.thisObject, "getResPosId") as? Int
                val args = chain.args.toTypedArray()
                if (resPosId == SPORT_RES_POS_ID) {
                    val source = chain.args.firstOrNull() as? List<*>
                    if (source != null) {
                        args[0] = ListFilters.copyAndFilter(source.filterNotNull(), { section ->
                            sectionKey(section)
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
                it.parameterTypes.getOrNull(0)?.name == "${context.points.sportColumnAdapter}\$e" &&
                it.parameterTypes.getOrNull(1) == Int::class.javaPrimitiveType
        } ?: return 0
        context.framework.hook(method).setId("$id:quick-entry-bind")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val holder = chain.args.firstOrNull()
                val root = holder?.let { ReflectionTargets.fieldValue(it, "cn") } as? View
                applyBoundQuickEntry(root, context)
                root?.post { applyBoundQuickEntry(root, context) }
                result
            }
        return 1
    }

    private fun applyBoundQuickEntry(root: View?, context: HookContext) {
        if (!ViewSelectors.hasAncestorResourceName(root, "sport_viewPager_container")) return
        val key = QUICK_ENTRY_TITLES[ViewSelectors.text(root)]
        if (key != null && context.config[key] == true) {
            root?.let(ViewTrimmer::collapse)
        } else {
            root?.let(ViewTrimmer::restore)
        }
    }

    private fun sectionKey(section: Any): String? {
        val info = ReflectionTargets.invokeNoArgs(section, "n")
        val resourceName = listOfNotNull(
            ReflectionTargets.invokeNoArgs(info ?: return null, "getResourceId") as? String,
            ReflectionTargets.invokeNoArgs(info, "getResourceName") as? String,
        ).joinToString("|")
        val provider = ReflectionTargets.invokeNoArgs(section, "c")?.javaClass?.name
        val title = ViewSelectors.text(ReflectionTargets.invokeNoArgs(section, "q") as? View)
        return SportContentKeyResolver.resolve(resourceName, provider, title)
    }

    private companion object {
        const val SPORT_RES_POS_ID = 4040
        val TOP_VIEWS = mapOf(
            "track_sport_tab" to SettingsKeys.SPORT_CATEGORY_BAR,
            "sport_search_icon" to SettingsKeys.SPORT_SEARCH,
            "sport_global_search_view" to SettingsKeys.SPORT_SEARCH,
            "more_and_red_point" to SettingsKeys.SPORT_MORE,
            "view_sport_banner_root" to SettingsKeys.SPORT_BANNER,
        )
        val layoutListeners = Collections.synchronizedMap(
            WeakHashMap<View, View.OnLayoutChangeListener>(),
        )
        val QUICK_ENTRY_TITLES = mapOf(
            "拉伸放松" to SettingsKeys.SPORT_STRETCH,
            "舒展放松" to SettingsKeys.SPORT_STRETCH,
            "古法养生" to SettingsKeys.SPORT_TRADITIONAL,
            "骑行课程" to SettingsKeys.SPORT_CYCLING,
            "高尔夫课" to SettingsKeys.SPORT_GOLF,
            "热汗舞蹈" to SettingsKeys.SPORT_DANCE,
            "普拉提课" to SettingsKeys.SPORT_PILATES,
        )
        val SECTION_TITLES = mapOf(
            "畅享运动" to SettingsKeys.SPORT_ENJOY,
            "今日动一动" to SettingsKeys.SPORT_TODAY,
            "更多好课" to SettingsKeys.SPORT_MORE_COURSES,
            "明星教练" to SettingsKeys.SPORT_COACHES,
        )
    }
}
