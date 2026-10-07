package love.nairain.huawei.watchface

/** 纯状态机：传输 100% 不是成功，必须收到应用成功及其后的设备列表确认。 */
internal class LocalInstallState(val id: String, val version: String) {
    enum class Phase { APPLYING, TRANSFERRING, APPLYING_TRANSFERRED, VERIFYING, SUCCEEDED, FAILED }
    @Volatile var phase = Phase.APPLYING
        private set
    val terminal get() = phase == Phase.SUCCEEDED || phase == Phase.FAILED

    @Synchronized fun readyToTransfer(): Boolean {
        if (phase != Phase.APPLYING) return false
        phase = Phase.TRANSFERRING
        return true
    }

    @Synchronized fun transferred(): Boolean {
        if (phase != Phase.TRANSFERRING) return false
        phase = Phase.APPLYING_TRANSFERRED
        return true
    }

    @Synchronized fun applied(expected: Phase = phase): Boolean {
        if (phase != expected) return false
        if (phase != Phase.APPLYING && phase != Phase.APPLYING_TRANSFERRED) return false
        phase = Phase.VERIFYING
        return true
    }

    @Synchronized fun verify(entries: Map<String, String>, observed: Phase? = phase): Boolean {
        if (observed != Phase.VERIFYING || phase != Phase.VERIFYING || entries[id] != version) return false
        phase = Phase.SUCCEEDED
        return true
    }

    @Synchronized fun fail(): Boolean {
        if (terminal) return false
        phase = Phase.FAILED
        return true
    }
}
