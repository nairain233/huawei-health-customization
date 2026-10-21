package love.nairain.huawei

import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.config.*
import love.nairain.huawei.hook.feature.SportContentViews
import love.nairain.huawei.hook.util.ViewSelectors
import org.junit.Assert.*
import org.junit.Test

/** 在设备上验证真实 View 折叠与恢复；不代表已注入宿主。 */
class SportContentViewsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val selectors = ViewSelectors(mapOf("id/header_title" to 1001, "id/item_quick_entry_title" to 1002,
        "id/item_quick_entry_root_layout" to 1003, "id/goal_card_button" to 1004))
    private fun root() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun title(text: String, id: Int) = TextView(context).apply { this.id = id; this.text = text }

    @Test fun golfGuideKeepsNativeHeaderAndRestoresWhenAnotherBannerAppears() = instrumentation.runOnMainSync {
        val processor = SportContentViews()
        val root = root()
        val card = FrameLayout(context).apply { id = 2001 }
        val normal = FrameLayout(context).apply { id = 2002; layoutParams = FrameLayout.LayoutParams(200, 300) }
        val mapButton = title("球场地图", 2003)
        val banner = FrameLayout(context).apply { id = 2004; layoutParams = FrameLayout.LayoutParams(200, 200) }
        val pager = FrameLayout(context).apply { id = 2005 }
        val item = FrameLayout(context).apply { id = 2006 }
        val guide = title("高尔夫球场模式使用指南", 2007)
        root.addView(card); card.addView(normal); normal.addView(mapButton); normal.addView(banner)
        banner.addView(pager); pager.addView(item); item.addView(guide)
        banner.layout(0, 100, 200, 300)
        val views = ViewSelectors(mapOf("id/section16_9card_01" to 2001, "id/normal_view" to 2002,
            "id/right_top_button" to 2003, "id/banner_root" to 2004, "id/view_pager_banner_cant_loop" to 2005,
            "id/recycle_item" to 2006, "id/item_title" to 2007))
        val choice = SportItem.fixed(286, "guide")
        val config = SportSimplificationConfig(overrides = mapOf(choice.id to true))
        processor.apply(root, 286, emptyList(), views, config, emptyMap())
        assertEquals(View.GONE, banner.visibility); assertEquals(100, normal.layoutParams.height)
        assertEquals(View.VISIBLE, mapButton.visibility)
        processor.apply(root, 286, emptyList(), views, config, emptyMap())
        assertEquals(View.GONE, banner.visibility); assertEquals(100, normal.layoutParams.height)
        processor.apply(root, 286, emptyList(), views, SportSimplificationConfig(), emptyMap())
        assertEquals(View.VISIBLE, banner.visibility); assertEquals(300, normal.layoutParams.height)
        banner.layout(0, 100, 200, 300)
        processor.apply(root, 286, emptyList(), views, config, emptyMap())
        pager.addView(title("未知 Banner", 2007))
        processor.apply(root, 286, emptyList(), views, config, emptyMap())
        assertEquals(View.VISIBLE, banner.visibility); assertEquals(300, normal.layoutParams.height)
        assertEquals(View.VISIBLE, mapButton.visibility)
    }

    @Test fun sameTitleStaysIsolatedAndUnknownOrDuplicateSectionsRemainVisible() = instrumentation.runOnMainSync {
        val processor = SportContentViews()
        val root = root()
        val section = root().also { it.addView(title("跳绳必备", 1001)); root.addView(it) }
        val item = SportFixedItems.items(283).first { it.hostTitle == "跳绳必备" }
        val rules = SportSimplificationConfig(overrides = mapOf(item.id to true))
        processor.apply(root, 259, listOf(section), selectors, rules, emptyMap())
        assertEquals(View.VISIBLE, section.visibility)
        processor.apply(root, 283, listOf(section), selectors, rules, emptyMap())
        assertEquals(View.GONE, section.visibility)
        val duplicate = root().also { it.addView(title("跳绳必备", 1001)); root.addView(it) }
        processor.apply(root, 283, listOf(section, duplicate), selectors, rules, emptyMap())
        assertEquals(View.VISIBLE, section.visibility)
        assertEquals(View.VISIBLE, duplicate.visibility)
        (section.getChildAt(0) as TextView).text = "新内容"
        processor.apply(root, 283, listOf(section, duplicate), selectors, rules, emptyMap())
        assertEquals(View.VISIBLE, section.visibility)
        processor.restore()
        assertEquals(View.VISIBLE, duplicate.visibility)
    }

    @Test fun recycledShortcutRestoresDimensionsBeforeUnknownItemBinds() = instrumentation.runOnMainSync {
        val processor = SportContentViews()
        val root = root()
        val entry = root().apply { id = 1003; layoutParams = LinearLayout.LayoutParams(120, 80) }
        val title = title("全部跑步课", 1002)
        entry.addView(title); root.addView(entry)
        val item = SportFixedItems.items(258).first { it.hostTitle == "全部跑步课" }
        val rules = SportSimplificationConfig(overrides = mapOf(item.id to true))
        processor.apply(root, 258, emptyList(), selectors, rules, emptyMap())
        assertEquals(View.GONE, entry.visibility); assertEquals(0, entry.layoutParams.width)
        processor.apply(root, 258, emptyList(), selectors, rules, emptyMap(), entriesEnabled = false)
        assertEquals(View.VISIBLE, entry.visibility)
        processor.apply(root, 258, emptyList(), selectors, rules, emptyMap())
        processor.restoreEntries()
        assertEquals(View.VISIBLE, entry.visibility)
        processor.apply(root, 258, emptyList(), selectors, rules, emptyMap())
        processor.beforeBind(entry)
        assertEquals(View.VISIBLE, entry.visibility); assertEquals(120, entry.layoutParams.width)
        title.text = "未知入口"
        processor.apply(root, 258, emptyList(), selectors, rules, emptyMap())
        assertEquals(View.VISIBLE, entry.visibility); assertEquals(80, entry.layoutParams.height)
    }

    @Test fun parentSectionPausesButtonAndRestoringParentReappliesButtonChoice() = instrumentation.runOnMainSync {
        val processor = SportContentViews()
        val root = root()
        val section = root().also { it.addView(title("我的瑜伽课程", 1001)); root.addView(it) }
        // 使用真实固定按钮资源名对应的选择器快照。
        val button = title("去选课", 1005).also { section.addView(it) }
        val views = ViewSelectors(mapOf("id/header_title" to 1001, "id/no_data_button" to 1005))
        val parent = SportFixedItems.items(137).first { it.hostTitle == "我的瑜伽课程" }
        val item = SportFixedItems.items(137).first { it.hostTitle == "去选课" }
        val rules = SportSimplificationConfig(overrides = mapOf(parent.id to true, item.id to true))
        processor.apply(root, 137, listOf(section), views, rules, emptyMap())
        assertEquals(View.GONE, section.visibility); assertEquals(View.VISIBLE, button.visibility)
        processor.apply(root, 137, listOf(section), views,
            rules.copy(overrides = rules.overrides + (parent.id to false)), emptyMap())
        assertEquals(View.VISIBLE, section.visibility); assertEquals(View.GONE, button.visibility)
        processor.restore(); assertEquals(View.VISIBLE, button.visibility)
    }
}
