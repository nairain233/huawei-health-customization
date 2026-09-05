package love.nairain.huawei.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** API 102 入口：这里只做包、主进程和首包守卫。 */
class HookEntry : XposedModule() {
    private var processName = ""

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!HookInstallPolicy.acceptsPackage(param.packageName, processName, param.isFirstPackage)) {
            return
        }
        HookCoordinator.installAttachHook(this, param.classLoader)
    }
}
