package love.nairain.huawei.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.hook.HookInstallPolicy

internal data class ServiceItem(
    val component: String,
    val process: String = "",
    val exported: Boolean = false,
    val enabled: Boolean = false,
    val missing: Boolean = false,
) {
    val className: String get() = component.substringAfter('/')
}

internal data class ServiceCatalog(
    val services: List<ServiceItem> = emptyList(),
    val supported: Boolean = false,
    val status: Int? = null,
) {
    companion object {
        /** 由页面工作线程调用，不扫描 Dex，不读取应用私有文件。 */
        fun load(manager: PackageManager): ServiceCatalog {
            val flags = PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getPackageInfo(HookInstallPolicy.TARGET_PACKAGE, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                manager.getPackageInfo(HookInstallPolicy.TARGET_PACKAGE, flags)
            }
            val services = info.services.orEmpty().mapNotNull { service ->
                val component = ServiceBlockConfig.componentName(service.packageName, service.name) ?: return@mapNotNull null
                val setting = manager.getComponentEnabledSetting(ComponentName(service.packageName, service.name))
                val enabled = when (setting) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> service.enabled
                    else -> false
                } && info.applicationInfo?.enabled != false
                ServiceItem(component, service.processName.orEmpty(), service.exported, enabled)
            }.distinctBy { it.component }.sortedBy { it.className }
            val supported = HookInstallPolicy.acceptsVersion(info.versionName, info.longVersionCode)
            return ServiceCatalog(services, supported,
                if (supported) null else love.nairain.huawei.R.string.service_block_unsupported)
        }
    }
}

internal fun visibleServices(
    services: List<ServiceItem>,
    components: Set<String>,
    query: String,
    blockedOnly: Boolean,
): List<ServiceItem> {
    val present = services.map { it.component }.toSet()
    val missing = (components - present).map { ServiceItem(it, missing = true) }
    return (services + missing).filter {
        (!blockedOnly || it.component in components) &&
            (it.className.contains(query.trim(), ignoreCase = true) || it.process.contains(query.trim(), ignoreCase = true))
    }.sortedBy { it.className }
}
