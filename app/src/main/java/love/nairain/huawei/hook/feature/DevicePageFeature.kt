package love.nairain.huawei.hook.feature

import android.view.View
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.HookFeature
import love.nairain.huawei.hook.InstallResult
import love.nairain.huawei.hook.installIsolated
import love.nairain.huawei.hook.resolver.DeviceContentKeyResolver
import love.nairain.huawei.hook.resolver.ReflectionTargets
import love.nairain.huawei.hook.util.ViewSelectors

class DevicePageFeature : HookFeature {
    override val id = "device.page"

    override fun install(context: HookContext): InstallResult {
        if (!SettingsCatalog.hasHidden(SettingsCategory.DEVICE, context.config)) {
            return InstallResult.Disabled
        }
        var count = 0
        context.points.deviceFragments.forEachIndexed { index, className ->
            count += context.installIsolated("device.fragment.$index") {
                installFragment(context, index, className)
            }
        }
        return if (count > 0) InstallResult.Installed(count)
        else InstallResult.Unsupported("verified device symbols unavailable")
    }

    private fun installFragment(context: HookContext, index: Int, className: String): Int {
        val type = ReflectionTargets.type(context.classLoader, className) ?: return 0
        var count = 0
        ReflectionTargets.method(type, "onCreateView", 3)?.let { method ->
            context.framework.hook(method).setId("$id:create:$index")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    apply(result as? View, context)
                    result
                }
            count++
        }
        ReflectionTargets.method(type, "onResume", 0)?.let { method ->
            context.framework.hook(method).setId("$id:resume:$index")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    apply(ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View, context)
                    result
                }
            count++
        }
        // 17.0.7.310 的营销资源刷新入口；每个入口独立安装，缺失时不影响生命周期 Hook。
        val refreshMethods = if (index == 0) {
            ReflectionTargets.methods(type, "a", 1).filter {
                List::class.java.isAssignableFrom(it.parameterTypes[0])
            }
        } else {
            ReflectionTargets.methods(type, "b", 2).filter {
                it.parameterTypes.lastOrNull()?.name == "java.util.Map"
            }
        }
        refreshMethods.forEachIndexed { refreshIndex, method ->
            context.framework.hook(method).setId("$id:refresh:$index:$refreshIndex")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    apply(ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View, context)
                    result
                }
            count++
        }
        return count
    }

    private fun apply(root: View?, context: HookContext) {
        if (root == null) return
        ViewSelectors.applyByResourceNames(
            root,
            context.application.packageName,
            DeviceContentKeyResolver.resourceMappings,
            context.config,
        )
    }
}
