package love.nairain.huawei.scan

import org.json.JSONArray
import org.json.JSONObject

/** 当前 APK 的完整定位证据；Hook 与缓存复核只消费这份快照。 */
data class LayoutResolution(
    val groups: Set<String>,
    val matched: Set<String>,
    val bindings: Map<String, String>,
    val descriptors: Set<String>,
    val mineManager: String,
    val failures: Map<String, String> = emptyMap(),
    val capabilities: Map<String, Set<String>> = emptyMap(),
    val resourceIds: Map<String, Int> = emptyMap(),
    val identities: Map<String, String> = emptyMap(),
    val requirements: Map<String, Set<String>> = emptyMap(),
    val issues: Map<String, String> = emptyMap(),
) {
    fun encode(): String = JSONObject().apply {
        put("rules", ScanProtocol.RULES)
        put("groups", JSONArray(groups.sorted())); put("matched", JSONArray(matched.sorted()))
        put("bindings", JSONObject(bindings)); put("descriptors", JSONArray(descriptors.sorted()))
        put("mine", mineManager); put("failures", JSONObject(failures))
        put("resources", JSONObject(resourceIds)); put("identities", JSONObject(identities))
        put("issues", JSONObject(issues))
        fun sets(values: Map<String, Set<String>>) = JSONObject().apply {
            values.forEach { (id, keys) -> put(id, JSONArray(keys.sorted())) }
        }
        put("capabilities", sets(capabilities)); put("requirements", sets(requirements))
    }.toString()

    companion object {
        fun decode(raw: String): LayoutResolution {
            val j = JSONObject(raw)
            require(j.getInt("rules") == ScanProtocol.RULES)
            fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }.toSet()
            fun map(key: String): Map<String, String> = j.getJSONObject(key).let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            }
            fun sets(key: String) = j.getJSONObject(key).let { obj ->
                obj.keys().asSequence().associateWith { strings(obj.getJSONArray(it)) }
            }
            val resources = j.getJSONObject("resources").let { obj ->
                obj.keys().asSequence().associateWith { obj.getInt(it) }
            }
            return LayoutResolution(strings(j.getJSONArray("groups")), strings(j.getJSONArray("matched")),
                map("bindings"), strings(j.getJSONArray("descriptors")), j.getString("mine"), map("failures"),
                sets("capabilities"), resources, map("identities"), sets("requirements"), map("issues")).also {
                require(ScanProtocol.keys.containsAll(it.matched + it.failures.keys))
                require(it.groups == it.capabilities.keys && it.groups == it.requirements.keys)
                require(it.capabilities.values.flatten().toSet() == it.matched)
                require(it.bindings.values.all(it.descriptors::contains))
                require(it.descriptors.none { descriptor -> "-><clinit>" in descriptor })
                require(resources.all { (key, id) -> key.matches(Regex("(id|string|layout)/[A-Za-z0-9_]+")) && id > 0 })
                val evidence = it.descriptors + resources.keys.map { key -> "resource:$key" } +
                    it.bindings.keys.map { key -> "binding:$key" } + it.identities.keys.map { key -> "identity:$key" }
                require(it.requirements.values.all(evidence::containsAll))
                require(it.requirements.values.all { required -> required.filter { key -> key.startsWith("binding:") }
                    .all { role -> it.bindings.getValue(role.removePrefix("binding:")) in required } })
                require("mine.rows" !in it.groups || it.bindings["${it.mineManager}#rows.domestic#0"] != null)
                require(it.identities.values.all(ScanProtocol.keys::contains))
                require(it.identities.filterKeys { key -> key.startsWith("content:") }.keys.all { role ->
                    role.removePrefix("content:").toInt() in resources.filterKeys { key -> key.startsWith("string/") }.values
                })
            }
        }
    }
}
