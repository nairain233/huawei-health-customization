package love.nairain.huawei

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageResourcesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun exposesCoreScreensInSimplifiedChineseAndEnglish() {
        val chinese = context.withLocale("zh-CN")
        val english = context.withLocale("en")

        assertEquals("华为运动精简", chinese.getString(R.string.app_name))
        assertEquals("语言", chinese.getString(R.string.settings_language))
        assertEquals("布局精简", chinese.getString(R.string.settings_layout_trim_title))
        assertEquals("后台服务精简", chinese.getString(R.string.service_block_title))
        assertEquals("dexkit扫描", chinese.getString(R.string.scan_title))
        assertEquals("关于", chinese.getString(R.string.about_title))
        assertEquals(
            "一款用于自定义华为运动健康布局的 LSPosed 模块，支持按需调整健康、运动、设备、“我的”页面和底栏内容，并可选择性屏蔽目标应用主动发起的后台服务调用。各项功能可独立开关，配置或定位异常时会安全跳过，尽量保持原应用行为不变。",
            chinese.getString(R.string.about_project_intro),
        )
        assertEquals("营销内容", chinese.getString(R.string.settings_group_marketing_content))
        assertEquals("我的页营销卡片", chinese.getString(R.string.settings_mine_marketing))

        assertEquals("Huawei Health Customization", english.getString(R.string.app_name))
        assertEquals("Language", english.getString(R.string.settings_language))
        assertEquals("Layout customization", english.getString(R.string.settings_layout_trim_title))
        assertEquals("Health page items", english.getString(R.string.settings_health_title))
        assertEquals("Exercise page items", english.getString(R.string.settings_sport_title))
        assertEquals("Devices page items", english.getString(R.string.settings_device_title))
        assertEquals("Me page items", english.getString(R.string.settings_mine_title))
        assertEquals("Marketing content", english.getString(R.string.settings_group_marketing_content))
        assertEquals("Me page marketing cards", english.getString(R.string.settings_mine_marketing))
        assertEquals("Bottom bar items", english.getString(R.string.settings_bottom_title))
        assertEquals("Background service customization", english.getString(R.string.service_block_title))
        assertEquals("DexKit scan", english.getString(R.string.scan_title))
        assertEquals("About", english.getString(R.string.about_title))
        assertEquals(
            "An LSPosed module for customizing Huawei Health. It provides on-demand controls for the Health, Exercise, Devices, Me, and bottom-bar layouts, plus optional blocking of explicitly selected service calls. Each feature can be enabled independently; configuration or target-resolution failures are safely skipped to preserve the original app behavior as much as possible.",
            english.getString(R.string.about_project_intro),
        )
    }

    @Test
    fun keepsFormattedResourceArgumentsInBothLanguages() {
        val chinese = context.withLocale("zh-CN")
        val english = context.withLocale("en")

        assertEquals(
            "扫描完成，已发现1/2，全部完成",
            chinese.getString(R.string.scan_complete_all, 1, 2),
        )
        assertEquals(
            "Scan complete. Found 1/2; all items are available",
            english.getString(R.string.scan_complete_all, 1, 2),
        )
        assertEquals(
            "Process: main\nExported: Yes · Enabled by system: No",
            english.getString(R.string.service_block_details, "main", "Yes", "No"),
        )
    }

    private fun Context.withLocale(languageTag: String): Context {
        val configuration = Configuration(resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(languageTag)))
        }
        return createConfigurationContext(configuration)
    }
}
