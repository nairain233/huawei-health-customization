package love.nairain.huawei.hook.feature

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import love.nairain.huawei.config.*
import love.nairain.huawei.hook.util.ViewSelectors
import love.nairain.huawei.hook.util.ViewTrimmer
import java.util.WeakHashMap

/** 只匹配本页固定清单和核验过的原生容器；不登记宿主新内容。 */
internal class SportContentViews {
    private val hidden = WeakHashMap<View, SportItemKind>()
    private val guideOffsets = WeakHashMap<View, Int>()
    private val headerHeights = WeakHashMap<View, Int>()
    private val sectionNames = setOf("series_course_layout", "layout_marketing_grid", "section_root_view")

    fun apply(root: View, page: Int, sections: List<View>, selectors: ViewSelectors,
        config: SportSimplificationConfig, layout: Map<String, Boolean>, entriesEnabled: Boolean = true) {
        val views = mutableListOf<View>()
        selectors.walk(root) { views += it }
        val candidates = linkedMapOf<SportItem, List<View>>()
        SportFixedItems.items(page).forEach { item ->
            val matches = when (item.kind) {
                SportItemKind.CONTROL -> views.filter { selectors.resourceEntryName(it) in item.resources }
                    .let { all -> all.filter { view -> ancestors(view, root).none { it in all } } }
                SportItemKind.GROUP -> views.filter { selectors.resourceEntryName(it) == "layout_marketing_grid" &&
                    descendants(it, selectors).any { child -> selectors.resourceEntryName(child) == "item_quick_entry_root_layout" } }
                else -> {
                    val headingNames = when (item.kind) {
                        SportItemKind.ENTRY -> setOf("item_quick_entry_title")
                        SportItemKind.PLAN -> setOf("item_two_landscape_title")
                        SportItemKind.GUIDE -> setOf("item_title")
                        else -> setOf("header_title", "hwsubheader_title_left")
                    }
                    views.filter { it is TextView && selectors.resourceEntryName(it) in headingNames &&
                        it.text?.toString() == item.hostTitle }.mapNotNull { heading ->
                        ancestors(heading, root).firstOrNull { ancestor ->
                            when (item.kind) {
                                SportItemKind.ENTRY -> selectors.resourceEntryName(ancestor) == "item_quick_entry_root_layout"
                                SportItemKind.PLAN -> selectors.resourceEntryName(ancestor) == "item_two_landscape_layout"
                                SportItemKind.GUIDE -> false
                                else -> ancestor in sections || selectors.resourceEntryName(ancestor) in sectionNames
                            }
                        }
                            ?: if (item.kind == SportItemKind.GUIDE) guideBanner(heading, root, selectors) else null
                    }.distinct()
                }
            }
            candidates[item] = matches
        }
        val desiredItems = candidates.filter { (item, matches) -> matches.size == 1 &&
            (item.kind != SportItemKind.ENTRY || entriesEnabled) && config.effectiveHidden(item, layout) }
        val desired = desiredItems.values.flatten().toSet()
        val headers = desiredItems.filterKeys { it.kind == SportItemKind.GUIDE }.values.flatten()
            .mapNotNull { banner -> (banner.parent as? View)?.let { normal ->
                guideOffsets[banner]?.let { normal to it }
            } }.toMap()
        headerHeights.keys.toList().filter { within(it, root) && it !in headers }.forEach(::restoreHeader)
        headers.forEach { (normal, height) ->
            normal.layoutParams?.let { params ->
                headerHeights.putIfAbsent(normal, params.height)
                if (params.height != height) { params.height = height; normal.layoutParams = params }
            }
        }
        hidden.keys.toList().filter { view -> within(view, root) && view !in desired }.forEach {
            ViewTrimmer.restore(it); hidden.remove(it)
        }
        desiredItems.forEach { (item, matches) -> matches.forEach { hidden[it] = item.kind; ViewTrimmer.collapse(it) } }
    }

    fun beforeBind(view: View) { hidden.remove(view); ViewTrimmer.restore(view) }
    fun restoreEntries() {
        hidden.entries.toList().filter { it.value == SportItemKind.ENTRY }.forEach { (view, _) ->
            ViewTrimmer.restore(view); hidden.remove(view)
        }
    }
    fun restore() {
        hidden.keys.toList().forEach(ViewTrimmer::restore); hidden.clear()
        headerHeights.keys.toList().forEach(::restoreHeader); guideOffsets.clear()
    }
    /** 实机指南是概览卡内的单页 Banner；只隐藏该 Banner，保留球场地图和原生卡头。 */
    private fun guideBanner(heading: View, root: View, selectors: ViewSelectors): View? {
        val chain = ancestors(heading, root).toList()
        val banner = chain.firstOrNull { selectors.resourceEntryName(it) == "banner_root" } ?: return null
        val normal = banner.parent as? View ?: return null
        if (selectors.resourceEntryName(normal) != "normal_view" ||
            chain.none { selectors.resourceEntryName(it) == "section16_9card_01" } ||
            chain.none { selectors.resourceEntryName(it) == "recycle_item" }) return null
        val children = descendants(banner, selectors)
        // Section16_9Card_01 的单/双页路径均预加载相邻页；遇到其他卡片或循环页时放行。
        if (children.count { selectors.resourceEntryName(it) == "item_title" } != 1 ||
            children.none { selectors.resourceEntryName(it) == "view_pager_banner_cant_loop" && it.isVisible } ||
            children.any { selectors.resourceEntryName(it) == "view_pager_banner" && it.isVisible }) return null
        val offset = if (ViewTrimmer.isCollapsed(banner)) guideOffsets[banner] else banner.top.takeIf { it > 0 }
        if (offset == null) return null
        guideOffsets[banner] = offset
        return banner
    }
    private fun restoreHeader(view: View) {
        val height = headerHeights.remove(view) ?: return
        view.layoutParams?.let { it.height = height; view.layoutParams = it }
    }
    private fun descendants(root: View, selectors: ViewSelectors) = buildList { selectors.walk(root) { add(it) } }
    private fun ancestors(view: View, root: View): Sequence<View> =
        generateSequence(view.parent as? View) { it.parent as? View }.takeWhile { it !== root }
    private fun within(view: View, root: View) = generateSequence(view) { it.parent as? View }.any { it === root }
}
