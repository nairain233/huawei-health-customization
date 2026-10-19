package love.nairain.huawei.scan

import love.nairain.huawei.hook.resolver.HostSettingsTargets
import love.nairain.huawei.hook.resolver.HomeMoreTargets
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
                "setLeftButtonVisibility(I)V", "setRightTextVisible(I)V", "setRightTextContent(Ljava/lang/String;)V",
                "setRightTextOnClickListener(Landroid/view/View\$OnClickListener;)V").forEach { method ->
                assertNotNull(method, bridge.getMethodData("${base}titlebar/CustomTitleBar;->$method"))
            }
            val home = requireNotNull(bridge.getClassData(HomeMoreTargets.FRAGMENT))
            assertNotNull(bridge.getMethodData("L${HomeMoreTargets.FRAGMENT.replace('.', '/')};->onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;"))
            val setter = requireNotNull(bridge.getMethodData("${base}titlebar/CustomTitleBar;->setRightButtonOnClickListener(Landroid/view/View\$OnClickListener;)V"))
            assertEquals(1, home.methods.count { method -> method.invokes.any { it.descriptor == setter.descriptor } })
            assertTrue(setter.invokes.any { it.name == "setOnClickListener" && it.paramTypeNames == listOf("android.view.View\$OnClickListener") })
            assertFalse(home.methods.any { method -> method.invokes.any { it.name == "setOnLongClickListener" } })
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
        val appBar = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "layout/health_commonui_app_bar.xml")).getElementsByTagName("*")
        val restart = (0 until appBar.length).map { appBar.item(it) as Element }
            .single { it.getAttribute("id") == "@+id/hwtoolbar_right_text" }
        assertEquals("com.huawei.ui.commonui.healthtextview.HealthTextView", restart.tagName)
        fun layoutElements(name: String): List<Element> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resources, "layout/$name.xml")).getElementsByTagName("*")
            return (0 until nodes.length).map { nodes.item(it) as Element }
        }
        assertEquals("com.huawei.ui.commonui.titlebar.CustomTitleBar", layoutElements("swipe_recycleview_layout")
            .single { it.getAttribute("id") == "@+id/health_tab_titlebar" }.tagName)
        val more = layoutElements("hwappbarpattern_ok_icon_layout")
        val moreContainer = more.single { it.getAttribute("id") == "@+id/hwappbarpattern_layout_ok_icon" }
        assertEquals("LinearLayout", moreContainer.tagName)
        assertSame(moreContainer, more.single { it.getAttribute("id") == "@+id/hwappbarpattern_ok_icon" }.parentNode)
        val bar = layoutElements("health_commonui_app_bar")
        assertEquals("@layout/hwappbarpattern_ok_icon_layout", bar.single { it.getAttribute("id") == "@+id/hwtoolbar_icon2" }.getAttribute("layout"))
        assertEquals(8, ScanProtocol.RULES)
    }
}
