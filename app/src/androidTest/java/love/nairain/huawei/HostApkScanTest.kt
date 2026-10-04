package love.nairain.huawei

import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import love.nairain.huawei.scan.HostResources
import love.nairain.huawei.scan.LayoutScanner
import love.nairain.huawei.scan.ScanProtocol
import org.junit.Assert.assertEquals
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

/** 以生产规则扫描已安装的宿主 APK，并校验真实类加载器中的完整描述符。 */
class HostApkScanTest {
    @Test
    fun scansInstalledHuaweiHealth() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = "com.huawei.health"
        val info = context.packageManager.getPackageInfo(packageName, 0)
        val resources = context.packageManager.getResourcesForApplication(packageName)
        val host = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
        val paths = listOf(info.applicationInfo!!.sourceDir) + info.applicationInfo!!.splitSourceDirs.orEmpty().sorted()
        System.loadLibrary("dexkit")
        val bridges = paths.map(DexKitBridge::create)
        try {
            val result = LayoutScanner(bridges,
                HostResources(resources, host.classLoader, packageName)::id,
                { descriptor ->
                    when {
                        '(' in descriptor -> DexMethod(descriptor).let {
                            if (it.isConstructor) it.getConstructorInstance(host.classLoader)
                            else it.getMethodInstance(host.classLoader)
                        }
                        "->" in descriptor -> DexField(descriptor).getFieldInstance(host.classLoader)
                        else -> DexClass(descriptor).getInstance(host.classLoader)
                    }
                }).scan { _, _ -> }
            assertEquals(result.failures.toString(), emptySet<String>(), ScanProtocol.keys - result.matched)
        } finally {
            bridges.forEach(DexKitBridge::close)
        }
    }
}
