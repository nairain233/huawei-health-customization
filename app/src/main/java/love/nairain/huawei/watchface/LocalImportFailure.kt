package love.nairain.huawei.watchface

import org.json.JSONObject

/** 无身份报告只允许关闭正在等待响应的独占任务，绝不能作为安装成功证据。 */
internal object LocalImportFailure {
    enum class Stage { READING, CONFIRMING, DEVICE_LIST, SIGNATURE, APPLYING, TRANSFERRING, VERIFYING }

    fun missingIdentityError(state: LocalInstallState?, locked: Boolean, cancelled: Boolean,
        sameDevice: Boolean, code: Int, identity: String?): Boolean =
        state != null && locked && !cancelled && sameDevice && code != 0 &&
            (identity.isNullOrEmpty() || identity == "null_null") &&
            state.phase in setOf(LocalInstallState.Phase.APPLYING, LocalInstallState.Phase.APPLYING_TRANSFERRED)

    fun signatureRejected(code: Int): Boolean = code == 100007 || code == 100014 || code == 100015

    fun timeoutStage(stage: Stage, now: Long, deadline: Long, started: Long, commandIssued: Boolean): Stage? =
        stage.takeIf { now > deadline || (commandIssued && now - started > 15 * 60_000) }

    // 宿主 k2 的 true 仅表示响应非空；仅提取业务码，绝不记录签名正文。
    fun signatureResult(raw: String?): Int? = try {
        val value = JSONObject(raw.orEmpty()).opt("resultcode")
        value?.toString()?.toIntOrNull()
    } catch (_: org.json.JSONException) { null }
}
