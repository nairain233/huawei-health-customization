package love.nairain.huawei.hook

import java.util.concurrent.atomic.AtomicBoolean

class HookInstallGate {
    private val attachInstalled = AtomicBoolean()
    private val runtimeInstalled = AtomicBoolean()

    fun claimAttach(): Boolean = attachInstalled.compareAndSet(false, true)

    fun claimRuntime(): Boolean = runtimeInstalled.compareAndSet(false, true)
}

object HookInstallPolicy {
    const val TARGET_PACKAGE = "com.huawei.health"
    const val SUPPORTED_VERSION_NAME = "17.0.7.310"
    const val SUPPORTED_VERSION_CODE = 1700007310L

    fun acceptsPackage(
        packageName: String?,
        processName: String?,
        isFirstPackage: Boolean,
    ): Boolean = packageName == TARGET_PACKAGE &&
        processName == TARGET_PACKAGE &&
        isFirstPackage

    /** 仅判断是否允许静态资源 ID/文案后备，不再作为安装守卫。 */
    fun acceptsVersion(versionName: String?, versionCode: Long): Boolean =
        !versionName.isNullOrBlank() &&
            versionName == SUPPORTED_VERSION_NAME &&
            versionCode == SUPPORTED_VERSION_CODE

    @Suppress("UNUSED_PARAMETER") // 保留调用接口；版本只用于扫描与缓存身份，不作为守卫。
    fun canInstallRuntime(
        attached: Boolean,
        packageName: String?,
        processName: String?,
        isFirstPackage: Boolean,
        versionName: String?,
        versionCode: Long,
    ): Boolean = attached && acceptsPackage(packageName, processName, isFirstPackage)
}
