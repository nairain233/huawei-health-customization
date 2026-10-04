package love.nairain.huawei.hook

import android.app.Application
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.hook.symbols.HuaweiHealthHookPoints
import love.nairain.huawei.hook.util.ModuleLogger

data class HookContext(
    val framework: XposedInterface,
    val classLoader: ClassLoader,
    val application: Application,
    val versionName: String,
    val versionCode: Long,
    val config: Map<String, Boolean>,
    val points: HuaweiHealthHookPoints,
    val logger: ModuleLogger,
    val resolvedGroups: Set<String>? = null,
    val resolvedCapabilities: Map<String, Set<String>>? = null,
    val resolution: love.nairain.huawei.scan.LayoutResolution = love.nairain.huawei.scan.LayoutResolution(emptySet(), emptySet(), emptyMap(), emptySet(), ""),
) {
    val hooks = TransactionalHooks(framework, logger)
    internal val targets = love.nairain.huawei.hook.resolver.ReflectionTargets(resolution, classLoader)
    val views = love.nairain.huawei.hook.util.ViewSelectors(resolution.resourceIds)

    internal fun forSource(source: String): HookContext = copy(
        config = sourceLayoutConfig(config, source, resolvedCapabilities),
    )
}

sealed interface InstallResult {
    data class Installed(val hookCount: Int) : InstallResult
    data object Disabled : InstallResult
    data class Unsupported(val reason: String) : InstallResult
    data class Failed(val reason: String) : InstallResult
}

interface HookFeature {
    val id: String
    fun install(context: HookContext): InstallResult
}

internal fun HookContext.installIsolated(source: String, block: () -> Int): Int = try {
    if (resolvedGroups != null && source !in resolvedGroups &&
        !(source.startsWith("bottom.tabs.") && "bottom.tabs" in resolvedGroups)) 0 else hooks.install(source, block)
} catch (error: Throwable) {
    logger.warn("Hook source skipped: $source, ${error.javaClass.simpleName}")
    0
}

/** 能力配置与安装事务共用同一个页面上下文，避免 copy 后丢失事务状态。 */
internal fun HookContext.installSource(source: String, block: (HookContext) -> Int): Int {
    val scoped = forSource(source)
    return scoped.installIsolated(source) { block(scoped) }
}

/** 同义配置键在其他页面命中，不能授权当前安装组使用未通过核验的定位。 */
internal fun sourceLayoutConfig(
    config: Map<String, Boolean>,
    source: String,
    capabilities: Map<String, Set<String>>?,
): Map<String, Boolean> = if (capabilities == null) config else config.mapValues { (key, value) ->
    value && (!key.startsWith("hide.") || key in capabilities[source].orEmpty())
}
