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

    fun acceptsVersion(versionName: String?, versionCode: Long): Boolean =
        !versionName.isNullOrBlank() &&
            versionName == SUPPORTED_VERSION_NAME &&
            versionCode == SUPPORTED_VERSION_CODE

    fun canInstallRuntime(
        attached: Boolean,
        packageName: String?,
        processName: String?,
        isFirstPackage: Boolean,
        versionName: String?,
        versionCode: Long,
    ): Boolean = attached && acceptsPackage(packageName, processName, isFirstPackage) &&
        acceptsVersion(versionName, versionCode)
}
