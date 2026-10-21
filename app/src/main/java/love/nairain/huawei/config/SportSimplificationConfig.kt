package love.nairain.huawei.config

import org.json.JSONArray
import org.json.JSONObject

/** 固定选择按业务 ID 隔离；旧动态覆盖值仅保留，不再执行。 */
data class SportSimplificationConfig(
    val hiddenTabs: Set<String> = emptySet(),
    val overrides: Map<String, Boolean> = emptyMap(),
) {
    fun hidesTab(identity: String, id: Int): Boolean =
        id in SportFixedItems.pageIds && (overrides["tab/$id"] ?: (identity.isNotEmpty() && "$identity/$id" in hiddenTabs))
    fun hidden(item: SportItem, layout: Map<String, Boolean>): Boolean =
        SportFixedItems.item(item.id)?.let { fixed ->
            overrides[fixed.id] ?: fixed.legacyKey?.let { layout[it] } ?: false
        } ?: false
    fun effectiveHidden(item: SportItem, layout: Map<String, Boolean>): Boolean = hidden(item, layout) &&
        (item.parentKey == null || !hidden(SportItem.fixed(item.pageId, item.parentKey), layout))
    fun canHideTab(identity: String, id: Int, available: List<Int>): Boolean = id in available &&
        available.any { it != id && !hidesTab(identity, it) }

    fun encode(): JSONObject = JSONObject().put("tabs", JSONArray(hiddenTabs.sorted()))
        .put("items", JSONObject(overrides))

    companion object {
        fun decode(json: JSONObject): SportSimplificationConfig {
            val tabs = json.getJSONArray("tabs")
            val items = json.getJSONObject("items")
            require(tabs.length() <= 256 && items.length() <= 1024)
            val hidden = (0 until tabs.length()).map {
                require(tabs.get(it) is String)
                tabs.getString(it).also { value -> require(value.matches(Regex("[a-f0-9]{64}/[0-9]+"))) }
            }.toSet()
            val values = items.keys().asSequence().associateWith {
                require(it.length <= 240 && items.get(it) is Boolean)
                items.getBoolean(it)
            }
            return SportSimplificationConfig(hidden, values)
        }
    }
}

/** 过滤副本；同批身份不唯一时整体放行，不能猜测索引。 */
object SportTabPolicy {
    fun <T> visible(source: List<T>, id: (T) -> Int?, config: SportSimplificationConfig, identity: String): List<T> {
        val ids = source.map(id)
        if (ids.any { it == null } || ids.distinct().size != ids.size) return source.toList()
        val result = source.filter { !config.hidesTab(identity, requireNotNull(id(it))) }
        return result.ifEmpty { source.take(1) }
    }
    fun target(requested: Int, visible: List<Int>): Int? = requested.takeIf { it in visible } ?: visible.firstOrNull()
}

