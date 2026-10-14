package love.nairain.huawei.watchface

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources

/** 只消费框架提供的模块安装信息，不通过包名重新查找被隐藏的模块。由工作线程调用。 */
internal object ModuleResources {
    fun load(manager: PackageManager, info: ApplicationInfo): Resources {
        require(info.packageName == "love.nairain.huawei")
        require(!info.sourceDir.isNullOrBlank())
        return manager.getResourcesForApplication(info)
    }
}
