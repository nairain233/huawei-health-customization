package love.nairain.huawei.scan

import org.json.JSONObject
import java.security.MessageDigest

/** 缓存损坏和请求变化都视为 miss；持久化由外层 AtomicFile 完成。 */
internal object ScanCache {
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
        return LayoutResolution.decode(payload).also { result -> result.descriptors.forEach(verify) }
    }
}
