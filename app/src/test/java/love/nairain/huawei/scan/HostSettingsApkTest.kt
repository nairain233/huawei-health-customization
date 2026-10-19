package love.nairain.huawei.scan

import love.nairain.huawei.hook.resolver.HostSettingsTargets
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** 对每版真实 APK 核验独立入口契约，不增加布局扫描组或改变缓存规则版本。 */
class HostSettingsApkTest {
    @Test fun verifiedSettingsLayoutAndNativeWidgetContractsExistInTheActualApk() {
        val native = System.getProperty("scan.native").orEmpty()
        assumeTrue("未配置真实 APK 和桌面 DexKit 库", native.isNotEmpty())
        val library = File(native)
        listOf("libunwind.dll", "libc++.dll").map { File(library.parentFile, it) }.filter { it.isFile }.forEach { System.load(it.absolutePath) }
        System.load(library.absolutePath)
        DexKitBridge.create(requireNotNull(System.getProperty("scan.apk"))).use { bridge ->
            val activity = HostSettingsTargets.ACTIVITY.replace('.', '/')
            assertNotNull(bridge.getMethodData("L$activity;->onCreate(Landroid/os/Bundle;)V"))
            val base = "Lcom/huawei/ui/commonui/"
            listOf("titlebar/CustomTitleBar", "healthtextview/HealthTextView", "switchbutton/HealthSwitchButton", "divider/HealthDivider")
                .forEach { widget -> assertNotNull(widget, bridge.getMethodData("$base$widget;-><init>(Landroid/content/Context;)V")) }
            assertNotNull(bridge.getMethodData("${base}titlebar/CustomTitleBar;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V"))
            listOf("setTitleText(Ljava/lang/String;)V", "setLeftButtonOnClickListener(Landroid/view/View\$OnClickListener;)V",
                "setLeftButtonVisibility(I)V").forEach { method ->
                assertNotNull(method, bridge.getMethodData("${base}titlebar/CustomTitleBar;->$method"))
            }
        }
        val resources = requireNotNull(File(requireNotNull(System.getProperty("scan.resources"))).parentFile?.parentFile)
        val layout = File(resources, "layout/hw_show_settings_app.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layout)
        val elements = document.getElementsByTagName("*")
        val all = (0 until elements.length).map { elements.item(it) as Element }
        fun named(id: String) = all.single { it.getAttribute("id") == "@+id/$id" }
        val container = named("app_setting_activity_layout")
        val goal = named("hw_goal_layout")
        assertSame(container, goal.parentNode)
        assertEquals("RelativeLayout", goal.tagName)
        assertSame(goal, named("goal_text_view").parentNode)
        assertSame(goal, named("goal_image_view").parentNode)
        assertEquals(8, ScanProtocol.RULES)
    }
}
