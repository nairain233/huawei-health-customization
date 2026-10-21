package love.nairain.huawei.scan

import org.json.JSONObject
import java.security.MessageDigest

/** 缓存损坏和请求变化都视为 miss；持久化由外层 AtomicFile 完成。 */
internal object ScanCache {
    fun verifyResources(resolution: LayoutResolution, resourceId: (String, String) -> Int) {
        resolution.resourceIds.forEach { (key, value) ->
            require(resourceId(key.substringAfter('/'), key.substringBefore('/')) == value)
        }
        // 规则版本保持不变；旧缓存若漏掉当前 APK 的新版会员容器，必须重新扫描。
        val membership = resourceId("member_benefit_container", "id")
        require(membership == (resolution.resourceIds["id/member_benefit_container"] ?: 0))
        if ("sport.fixed" in resolution.groups) {
            love.nairain.huawei.config.SportFixedItems.resourceNames.forEach { name ->
                require(resourceId(name, "id") == (resolution.resourceIds["id/$name"] ?: 0))
            }
        }
    }

    private fun checksum(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun encode(identity: String, request: String, resolution: LayoutResolution): String {
        val payload = resolution.encode()
        return JSONObject().put("identity", identity).put("request", request)
            .put("checksum", checksum(payload)).put("resolution", payload).toString()
    }

    fun decode(raw: String, identity: String, request: String, verify: (String) -> Unit): LayoutResolution {
        require(raw.length <= 1_048_576)
        val json = JSONObject(raw)
        require(json.getString("identity") == identity && json.getString("request") == request)
        val payload = json.getString("resolution")
        require(json.getString("checksum") == checksum(payload))
        return LayoutResolution.decode(payload).also { result ->
            // 同一规则版本新增开关时，旧缓存必须重新扫描，不能冒充已检查新能力。
            require(result.matched + result.failures.keys == ScanProtocol.keys)
            // 开关键未变也必须确认检查过新增路径；明确失败的扫描结果仍可缓存。
            listOf("mine.badges.grid", "mine.badges.kaka").forEach { group ->
                require(love.nairain.huawei.config.SettingsKeys.MINE_BADGES in result.capabilities[group].orEmpty() ||
                    "$group:${love.nairain.huawei.config.SettingsKeys.MINE_BADGES}" in result.issues)
            }
            listOf("sport.identity", "sport.navigation", "sport.discovery", "sport.quick.discovery", "sport.fixed").forEach { group ->
                require(group in result.groups || group in result.issues)
            }
            val sportRoles = mapOf(
                "sport.identity" to setOf("sport.tabs#1", "sport.tab.id#0", "sport.tab.fragment#0", "sport.tab.title#field", "sport.tabs.current#field"),
                "sport.navigation" to setOf("sport.current#field", "sport.add#0", "sport.add.bean#field", "sport.add.owner#field",
                    "sport.remove#0", "sport.remove.bean#field", "sport.remove.owner#field", "sport.jump#2", "sport.order#1"),
                "sport.discovery" to setOf("getPageResTrigger#0", "onCreateView#3", "setCacheBeansList#1", "section.info#0", "section.view#0", "getResourceId#0", "getResourceName#0"),
                "sport.quick.discovery" to setOf("sport.quick.content#2", "getName#0", "getLinkValue#0"),
            )
            sportRoles.filterKeys { it in result.groups }.forEach { (group, roles) ->
                val required = result.requirements.getValue(group).filter { it.startsWith("binding:") }
                    .map { it.substringAfter('#') }.toSet()
                require(required.containsAll(roles))
            }
            if ("sport.fixed" in result.groups) {
                require(result.requirements.getValue("sport.fixed").containsAll(result.requirements.getValue("sport.discovery")))
                require("id/header_title" in result.resourceIds && "id/hwsubheader_title_left" in result.resourceIds)
            }
            result.descriptors.forEach(verify)
        }
    }
}
