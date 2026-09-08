package love.nairain.huawei.scan

import org.json.JSONArray
import org.json.JSONObject
import love.nairain.huawei.config.SettingsCatalog

/** 扫描协议与配置目录独立；匹配数不是开关数或 Hook 安装数。 */
object ScanProtocol {
    const val VERSION = 1
    const val RULES = 1
    const val REQUEST = "scan.request"
    const val AUTHORITY = "love.nairain.huawei.scan"
    const val STORE = "scan_reports"
    const val STALE_MS = 120_000L
    val keys: Set<String> get() = SettingsCatalog.all.map { it.key }.filter { it.startsWith("hide.") }.toSet()
}

data class ScanReport(
    val identity: String,
    val version: String,
    val versionCode: Long,
    val updated: Long,
    val request: String,
    val run: String,
    val started: Long,
    val sequence: Int = 0,
    val phase: String = "running",
    val checked: Set<String> = emptySet(),
    val matched: Set<String> = emptySet(),
    val time: Long = System.currentTimeMillis(),
    val service: String = "disabled",
    val error: String = "",
    val rules: Int = ScanProtocol.RULES,
) {
    fun encode(): String = JSONObject().apply {
        put("protocol", ScanProtocol.VERSION); put("rules", rules)
        put("identity", identity); put("version", version); put("code", versionCode); put("updated", updated)
        put("request", request); put("run", run); put("started", started); put("sequence", sequence)
        put("phase", phase); put("checked", JSONArray(checked.sorted())); put("matched", JSONArray(matched.sorted()))
        put("time", time); put("service", service); put("error", error)
    }.toString()

    fun follows(previous: ScanReport?): Boolean = previous == null ||
        (run != previous.run && started > previous.started) ||
        (run == previous.run && started == previous.started && (identity == previous.identity || previous.identity == "pending") &&
            request == previous.request && sequence > previous.sequence && previous.phase == "running" &&
            checked.containsAll(previous.checked))

    companion object {
        fun decode(raw: String): ScanReport {
            require(raw.length <= 32768)
            val json = JSONObject(raw)
            require(json.getInt("protocol") == ScanProtocol.VERSION)
            fun ids(name: String): Set<String> {
                val array = json.getJSONArray(name)
                require(array.length() <= ScanProtocol.keys.size)
                return (0 until array.length()).map { array.getString(it) }.toSet().also {
                    require(ScanProtocol.keys.containsAll(it))
                }
            }
            fun short(name: String, max: Int = 160): String = json.getString(name).also { require(it.length <= max) }
            return ScanReport(short("identity"), short("version"), json.getLong("code"), json.getLong("updated"),
                short("request"), short("run"), json.getLong("started"), json.getInt("sequence"), short("phase"),
                ids("checked"), ids("matched"), json.getLong("time"), short("service"), short("error"), json.getInt("rules"))
                .also {
                    require(it.phase in setOf("running", "complete", "failed"))
                    require(it.service in setOf("disabled", "installed", "unsupported"))
                    require(it.error in setOf("", "scan_error", "cache_error"))
                    require(it.checked.containsAll(it.matched) && it.sequence >= 0 && it.started > 0 && it.time >= it.started)
                    require(it.identity == "pending" || it.identity.matches(Regex("[0-9a-f]{64}")))
                    require(it.run.matches(Regex("[0-9a-f-]{36}")))
                    require(it.phase != "complete" || it.checked == ScanProtocol.keys)
                }
        }
    }
}
