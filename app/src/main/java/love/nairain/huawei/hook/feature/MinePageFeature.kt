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
import love.nairain.huawei.hook.resolver.RowKeyResolver
import love.nairain.huawei.hook.util.ViewSelectors
import java.util.concurrent.ConcurrentHashMap

class MinePageFeature : HookFeature {
    override val id = "mine.page"
    private val rowResolver = RowKeyResolver()
    private val loggedRows = ConcurrentHashMap.newKeySet<String>()

    override fun install(context: HookContext): InstallResult {
        if (!SettingsCatalog.hasHidden(SettingsCategory.MINE, context.config)) {
            return InstallResult.Disabled
        }
        var count = 0
        count += context.installIsolated("mine.header") { installHeader(context) }
        count += context.installIsolated("mine.grid") { installGrid(context) }
        count += context.installIsolated("mine.rows") { installRows(context) }
        return if (count > 0) InstallResult.Installed(count)
        else InstallResult.Unsupported("verified mine symbols unavailable")
    }

    private fun installHeader(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader, context.points.mineFragment) ?: return 0
        var count = 0
        ReflectionTargets.method(type, "onCreateView", 3)?.let { method ->
            context.framework.hook(method).setId("$id:header:create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    applyHeader(result as? View, context)
                    result
                }
            count++
        }
        ReflectionTargets.method(type, "onResume", 0)?.let { method ->
            context.framework.hook(method).setId("$id:header:resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    applyHeader(ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View, context)
                    result
                }
            count++
        }
        return count
    }

    private fun installGrid(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader, context.points.mineGridAdapter) ?: return 0
        var count = 0
        ReflectionTargets.constructor(type, 2)?.let { constructor ->
            context.framework.hook(constructor).setId("$id:grid:init")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    chain.proceed(filteredGridArguments(chain.args, 1, context))
                }
            count++
        }
        ReflectionTargets.method(type, "c", 1)?.let { method ->
            context.framework.hook(method).setId("$id:grid:refresh")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    chain.proceed(filteredGridArguments(chain.args, 0, context))
                }
            count++
        }
        return count
    }

    private fun installRows(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader, context.points.mineListManager) ?: return 0
        val methods = listOf("t", "l").mapNotNull { ReflectionTargets.method(type, it, 0, List::class.java) }
        methods.forEach { method ->
            context.framework.hook(method).setId("$id:rows:${method.name}")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    val source = result as? List<*> ?: return@intercept result
                    val filtered = ListFilters.copyAndFilter(source.filterNotNull(), { row ->
                        rowKey(row, context)
                    }, context.config)
                    ListFilters.cleanSections(
                        filtered,
                        isDivider = { row -> ReflectionTargets.invokeNoArgs(row, "j") == 1 },
                        isTitledDivider = { row ->
                            (ReflectionTargets.invokeNoArgs(row, "a") as? Int ?: 0) > 0
                        },
                    )
                }
        }
        return methods.size
    }

    private fun applyHeader(root: View?, context: HookContext) {
        if (root == null) return
        ViewSelectors.applyByResourceNames(
            root,
            context.application.packageName,
            HEADER_VIEWS,
            context.config,
        )
    }

    private fun filteredGridArguments(args: List<Any>, index: Int, context: HookContext): Array<Any> {
        val replacement = args.toTypedArray()
        val source = args.getOrNull(index) as? List<*> ?: return replacement
        replacement[index] = ListFilters.copyAndFilter(source.filterNotNull(), { model ->
            GRID_TYPES[model.javaClass.simpleName]
        }, context.config)
        return replacement
    }

    private fun rowKey(row: Any, context: HookContext): String? {
        val resourceId = ReflectionTargets.invokeNoArgs(row, "a") as? Int ?: return null
        if (resourceId <= 0) return null
        val name = runCatching {
            context.application.resources.getResourceEntryName(resourceId)
        }.getOrNull()
        val key = rowResolver.resolve(name, resourceId)
        if (key == null && name != null && loggedRows.add(name)) {
            context.logger.info("Mine row kept: unmapped resource=$name")
        }
        return key
    }

    private companion object {
        val HEADER_VIEWS = mapOf(
            "rl_actionbar_right" to SettingsKeys.MINE_MESSAGES,
            "rl_message_new" to SettingsKeys.MINE_MESSAGES,
            "account_center_customheadview" to SettingsKeys.MINE_ACCOUNT,
            "head_layout" to SettingsKeys.MINE_ACCOUNT,
            "vip_layout" to SettingsKeys.MINE_VIP,
        )
        val GRID_TYPES = mapOf(
            "wrq" to SettingsKeys.MINE_GROUP,
            "wrl" to SettingsKeys.MINE_FAMILY,
            "wrb" to SettingsKeys.MINE_ANNUAL_GOAL,
            "wsa" to SettingsKeys.MINE_REPORTS,
        )
    }
}
