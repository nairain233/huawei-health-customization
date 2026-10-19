package love.nairain.huawei.settings

import android.os.Bundle
import love.nairain.huawei.config.SettingsCategory

/** 只保存界面状态；配置值始终从控制器重新读取。 */
internal class HostSettingsPageState {
    var open = false
    var route = "home"
    val scroll = mutableMapOf<String, Int>()
    var draftQuery = ""
    var query = ""
    var selectedOnly = false
    var customIndex = 0
    var customOffset = 0

    fun resetNavigation() {
        route = "home"
        scroll.clear()
        draftQuery = ""
        query = ""
        selectedOnly = false
        customIndex = 0
        customOffset = 0
    }

    fun save(): Bundle = Bundle().apply {
        putBoolean("open", open)
        putString("route", route)
        putBundle("scroll", Bundle().also { bundle -> scroll.forEach { (key, value) -> bundle.putInt(key, value) } })
        putString("draftQuery", draftQuery)
        putString("query", query)
        putBoolean("selectedOnly", selectedOnly)
        putInt("customIndex", customIndex)
        putInt("customOffset", customOffset)
    }

    fun restore(bundle: Bundle?) {
        open = bundle?.getBoolean("open") == true
        route = bundle?.getString("route")?.takeIf { it in routes } ?: "home"
        scroll.clear()
        bundle?.getBundle("scroll")?.let { values ->
            values.keySet().filter { it in routes }.forEach { scroll[it] = values.getInt(it).coerceAtLeast(0) }
        }
        draftQuery = bundle?.getString("draftQuery").orEmpty()
        query = bundle?.getString("query").orEmpty()
        selectedOnly = bundle?.getBoolean("selectedOnly") == true
        customIndex = bundle?.getInt("customIndex")?.coerceAtLeast(0) ?: 0
        customOffset = bundle?.getInt("customOffset")?.coerceAtLeast(0) ?: 0
    }

    companion object {
        val routes = setOf("home", "layout", "experimental", "services", "custom", "scan", "about", "licenses") +
            SettingsCategory.entries.map { it.name }
        fun parent(route: String): String = when (route) {
            "home" -> "close"
            "custom" -> "services"
            "services" -> "experimental"
            "licenses" -> "about"
            in SettingsCategory.entries.map { it.name } -> "layout"
            else -> "home"
        }
    }
}
