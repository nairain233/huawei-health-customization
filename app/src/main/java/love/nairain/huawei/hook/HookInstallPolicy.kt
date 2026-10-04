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

    fun acceptsPackage(
        packageName: String?,
        processName: String?,
        isFirstPackage: Boolean,
    ): Boolean = packageName == TARGET_PACKAGE &&
        processName == TARGET_PACKAGE &&
        isFirstPackage

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
