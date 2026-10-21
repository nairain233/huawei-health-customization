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
    @Test fun verifiedEntryLayoutAndNativeWidgetContractsExistInTheActualApk() {
        val native = System.getProperty("scan.native").orEmpty()
        assumeTrue("未配置真实 APK 和桌面 DexKit 库", native.isNotEmpty())
        val library = File(native)
        listOf("libunwind.dll", "libc++.dll").map { File(library.parentFile, it) }.filter { it.isFile }.forEach { System.load(it.absolutePath) }
        System.load(library.absolutePath)
        val session = HostDexSession(listOf(requireNotNull(System.getProperty("scan.apk")))) { System.load(library.absolutePath) }
        var entryBridge: DexKitBridge? = null
        assertThrows(IllegalArgumentException::class.java) {
            session.entry { bridges -> entryBridge = bridges.single(); throw IllegalArgumentException("入口失败") }
        }
        try {
            session.layout { bridges ->
                assertSame("入口失败后布局仍复用同一桥", entryBridge, bridges.single())
                assertNotNull(bridges.single().getClassData(HomeMoreTargets.FRAGMENT))
            }
        } finally { session.finishLayout() }
        assertThrows(IllegalStateException::class.java) { session.layout { error("会话不得重新打开") } }
        // 布局命中缓存时可以先结束该阶段，入口仍可查询并在结束后关闭会话。
        val cachedSession = HostDexSession(listOf(requireNotNull(System.getProperty("scan.apk")))) { System.load(library.absolutePath) }
        cachedSession.finishLayout()
        cachedSession.entry { bridges -> assertNotNull(bridges.single().getClassData(HomeMoreTargets.FRAGMENT)) }
        assertThrows(IllegalStateException::class.java) { cachedSession.entry { error("会话不得重新打开") } }
        DexKitBridge.create(requireNotNull(System.getProperty("scan.apk"))).use { bridge ->
            val activity = HostSettingsTargets.ACTIVITY.replace('.', '/')
            assertNotNull(bridge.getMethodData("L$activity;->onCreate(Landroid/os/Bundle;)V"))
            val base = "Lcom/huawei/ui/commonui/"
            listOf("healthtextview/HealthTextView", "divider/HealthDivider")
                .forEach { widget -> assertNotNull(widget, bridge.getMethodData("$base$widget;-><init>(Landroid/content/Context;)V")) }
            val home = requireNotNull(bridge.getClassData(HomeMoreTargets.FRAGMENT))
            assertNotNull(bridge.getMethodData("L${HomeMoreTargets.FRAGMENT.replace('.', '/')};->onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;"))
            val setter = requireNotNull(bridge.getMethodData("${base}titlebar/CustomTitleBar;->setRightButtonOnClickListener(Landroid/view/View\$OnClickListener;)V"))
            assertEquals(1, home.methods.count { method -> method.invokes.any { it.descriptor == setter.descriptor } })
            assertTrue(setter.invokes.any { it.name == "setOnClickListener" && it.paramTypeNames == listOf("android.view.View\$OnClickListener") })
            assertFalse(home.methods.any { method -> method.invokes.any { it.name == "setOnLongClickListener" } })
            val resources = File(requireNotNull(System.getProperty("scan.resources"))).readText()
            val ids = Regex("<public type=\"id\" name=\"([^\"]+)\" id=\"0x([0-9a-fA-F]+)\"")
                .findAll(resources).associate { it.groupValues[1] to it.groupValues[2].toLong(16).toInt() }
            val binding = HomeMoreDiscovery.resolve(listOf(bridge)) { name, _ -> ids[name] ?: 0 }
            assertNotNull(bridge.getMethodData(binding.create))
            assertNotNull(bridge.getMethodData(binding.bind))
            assertNotNull(bridge.getFieldData(binding.titleField))
            assertNotNull(bridge.getFieldData(binding.clickField))
            assertEquals(HomeMoreDiscovery.resourceNames.map { ids.getValue(it) }, binding.ids)
            HomeMoreDiscovery.resourceNames.forEach { missing ->
                assertThrows(IllegalArgumentException::class.java) {
                    HomeMoreDiscovery.resolve(listOf(bridge)) { name, _ -> if (name == missing) 0 else ids[name] ?: 0 }
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                HomeMoreDiscovery.resolve(listOf(bridge)) { _, _ -> 1 }
            }
            assertThrows(IllegalArgumentException::class.java) {
                HomeMoreDiscovery.resolve(listOf(bridge, bridge)) { name, _ -> ids[name] ?: 0 }
            }
            System.getProperty("scan.output").orEmpty().takeIf { it.isNotEmpty() }?.let {
                File("$it.home-more.txt").writeText("create=${binding.create}\nbind=${binding.bind}\ntitle=${binding.titleField}\nclick=${binding.clickField}\nids=${binding.ids}\n")
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
