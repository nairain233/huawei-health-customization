package love.nairain.huawei

import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import love.nairain.huawei.hook.HookInstallPolicy
import love.nairain.huawei.scan.HostResources
import love.nairain.huawei.scan.LayoutScanner
import love.nairain.huawei.scan.ScanProtocol
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge

/** 在设备上以生产规则扫描已安装的基准 APK，补足桌面无 native 库时的验证。 */
class HostApkScanTest {
    @Test
    fun scansInstalledHuaweiHealth() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = "com.huawei.health"
        val info = context.packageManager.getPackageInfo(packageName, 0)
        assumeTrue(HookInstallPolicy.acceptsVersion(info.versionName, info.longVersionCode))
        val resources = context.packageManager.getResourcesForApplication(packageName)
        val host = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
        val paths = listOf(info.applicationInfo!!.sourceDir) + info.applicationInfo!!.splitSourceDirs.orEmpty().sorted()
        System.loadLibrary("dexkit")
        val bridges = paths.map(DexKitBridge::create)
        try {
            val result = LayoutScanner(bridges,
                HostResources(resources, host.classLoader, packageName)::id,
                {}, true).scan { _, _ -> }
            assertEquals(result.failures.toString(), emptySet<String>(), ScanProtocol.keys - result.matched)
        } finally {
            bridges.forEach(DexKitBridge::close)
        }
    }
}
