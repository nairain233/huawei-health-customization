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
) {
    val hooks = TransactionalHooks(framework, logger)
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
        !(source.startsWith("bottom.tabs.") && "bottom.tabs" in resolvedGroups)) 0 else hooks.install(block)
} catch (error: Throwable) {
    logger.warn("Hook source skipped: $source", error)
    0
}
