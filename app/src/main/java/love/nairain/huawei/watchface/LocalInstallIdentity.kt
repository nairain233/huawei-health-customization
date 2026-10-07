package love.nairain.huawei.watchface

/** 使用已实测的原生设计师槽位，版本仍来自宿主；防止新任务复用迟到回调的身份。 */
internal object LocalInstallIdentity {
    const val ID = "000000001"

    fun version(current: String?, retired: Set<String>, next: () -> String): String {
        repeat(128) {
            val value = next()
            require(value.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
            if (value != current && value !in retired) return value
        }
        error("Native version allocation failed")
    }
}
