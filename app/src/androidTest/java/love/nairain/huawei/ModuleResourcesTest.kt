package love.nairain.huawei

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.watchface.ModuleResources
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 验证宿主资源接收者不会遮蔽模块资源表。 */
@RunWith(AndroidJUnit4::class)
class ModuleResourcesTest {
    @Test fun moduleTitleUsesModuleAssetsInsideAHostViewReceiver() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val moduleResources = ModuleResources.load(app.packageManager, ApplicationInfo(app.applicationInfo))
        // Android 系统资源模拟宿主接收者；模块文案必须仍由传入的模块 AssetManager 提供。
        val host = object : ContextWrapper(app) {
            override fun getResources(): android.content.res.Resources = android.content.res.Resources.getSystem()
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            android.widget.TextView(host).apply {
                assertNotSame(moduleResources.assets, resources.assets)
                val localized = ModuleResources.localized(moduleResources, resources)
                text = localized.getString(R.string.settings_title)
                assertEquals("love.nairain.huawei", localized.getResourcePackageName(R.string.settings_title))
                assertEquals(app.getString(R.string.settings_title), text.toString())
                assertEquals(host.resources.configuration.locales, localized.configuration.locales)
            }
        }
    }

    @Test fun resourcesAndFooterLoadWithoutPackageContextLookup() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(app) {
            override fun createPackageContext(packageName: String, flags: Int): Context =
                error("Package context lookup must not be used")
        }
        val resources = ModuleResources.load(context.packageManager, ApplicationInfo(app.applicationInfo))
        assertEquals(app.getString(R.string.wf_import), resources.getString(R.string.wf_import))
        assertEquals("love.nairain.huawei", resources.getResourcePackageName(R.string.wf_import))
        assertTrue(resources.assets.open("local-watchface-footer.js").bufferedReader().use { it.readText() }.isNotBlank())
        assertThrows(IllegalArgumentException::class.java) {
            ModuleResources.load(context.packageManager, ApplicationInfo(app.applicationInfo).apply { packageName = "other" })
        }
    }

}
