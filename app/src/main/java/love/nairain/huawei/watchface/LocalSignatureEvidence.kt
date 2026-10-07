package love.nairain.huawei.watchface

import java.security.MessageDigest
import org.json.JSONObject

/** 只产生脱敏比较结果，不保留或输出签名、设备身份和哈希正文。 */
internal object LocalSignatureEvidence {
    data class Result(val identity: String, val hash: String, val status: Int?)

    fun digest(payload: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(payload).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun inspect(raw: String?, id: String, version: String, payloadDigest: String?): Result {
        val content = try { JSONObject(raw.orEmpty()).optJSONObject("result")?.optJSONObject("content") }
            catch (_: org.json.JSONException) { null }
        if (content == null) return Result("missing", "missing", null)
        val identity = when {
            !content.has("hitopId") || !content.has("version") -> "missing"
            content.optString("hitopId") == id && content.optString("version") == version -> "match"
            else -> "mismatch"
        }
        val hash = content.optString("watchContentHash").let {
            when {
                it.isBlank() -> "missing"
                it.all { value -> value == '0' } -> "zero"
                !it.matches(Regex("[0-9a-fA-F]{64}")) || payloadDigest == null -> "unrecognized"
                it.equals(payloadDigest, true) -> "match"
                else -> "mismatch"
            }
        }
        return Result(identity, hash, content.opt("status")?.toString()?.toIntOrNull())
    }
}
