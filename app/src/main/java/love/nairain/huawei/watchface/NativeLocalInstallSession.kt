package love.nairain.huawei.watchface

/** 原生异步任务的身份与许可。锁内只更新内存，不调用宿主、磁盘或界面。 */
internal class NativeLocalInstallSession(val state: LocalInstallState, val payloadPath: String) {
    enum class TransferRoute { ORIGINAL, LOCAL, SKIP }
    val taskId = "${state.id}_${state.version}"
    private var payloadRunning = true
    private var cacheReady = false
    private var signaturePending = false
    private var signatureRunning = false
    private var continuationPending = false
    private var continuationRunning = false
    private var firstApply = false
    private var secondApply = false
    private var transferPending = false
    private var transferScheduled = false
    private var transferStarted = false
    private var transferDispatchRunning = false
    private var transferEnded = false
    private var stopped = false
    @Volatile var cancelled = false
        private set
    @Volatile var hostReused = false
        private set
    @get:Synchronized val commandIssued get() = firstApply
    @get:Synchronized val observesFileCallbacks get() = transferStarted && !transferEnded && !cancelled

    fun matches(id: String?, version: String?) = id == state.id && version == state.version

    @Synchronized fun transferRoute(active: Boolean, fromLocalCallback: Boolean, path: Any?): TransferRoute = when {
        active -> if (cancelled) TransferRoute.SKIP else TransferRoute.LOCAL
        fromLocalCallback || path == payloadPath -> TransferRoute.SKIP
        else -> TransferRoute.ORIGINAL
    }

    /** 用户重新通过普通宿主流程操作同一 ID 后，其原生回调不再归属于旧本地会话。 */
    @Synchronized fun reuseByHost() {
        if (cancelled && canRelease()) hostReused = true
    }

    @Synchronized fun cache(payload: Any?): Boolean {
        if (cancelled || cacheReady || payload !is ByteArray || payload.isEmpty()) return false
        cacheReady = true
        signaturePending = true
        return true
    }

    @Synchronized fun payloadReturned() { payloadRunning = false }

    @Synchronized fun beginSignature(): Boolean {
        if (!signaturePending) return false
        signaturePending = false
        if (cancelled || !cacheReady) return false
        signatureRunning = true
        continuationPending = true
        return true
    }

    @Synchronized fun signatureReturned() { signatureRunning = false }

    @Synchronized fun beginContinuation(): Boolean {
        if (!continuationPending) return false
        continuationPending = false
        if (cancelled || !cacheReady) return false
        continuationRunning = true
        return true
    }

    @Synchronized fun continuationReturned() { continuationRunning = false }

    @Synchronized fun apply(signed: Boolean): Boolean {
        if (cancelled || !cacheReady) return false
        if (signed) {
            if (!continuationRunning || firstApply) return false
            firstApply = true
        } else {
            if (!transferEnded || secondApply || state.phase != LocalInstallState.Phase.APPLYING_TRANSFERRED) return false
            secondApply = true
        }
        return true
    }

    @Synchronized fun permitsOperation(signed: Boolean): Boolean = !cancelled &&
        if (signed) firstApply && !secondApply else secondApply

    /** 设计师入口与随机 ID 的 105 分支共享同一个传输许可。 */
    @Synchronized fun scheduleTransfer(): Boolean {
        if (cancelled || !firstApply || transferScheduled) return false
        transferScheduled = true
        transferPending = true
        return state.readyToTransfer()
    }

    @Synchronized fun beginTransfer(): Boolean {
        if (!transferPending) return false
        transferPending = false
        if (cancelled || transferStarted) return false
        transferStarted = true
        transferDispatchRunning = true
        return true
    }

    @Synchronized fun transferReturned() { transferDispatchRunning = false }

    // 停止命令必须晚于正在进入的原生传输调用，否则停止成功后仍可能启动传输。
    @Synchronized fun canRequestStop(): Boolean = !transferDispatchRunning && !continuationRunning

    @Synchronized fun transferred(): Boolean {
        if (!transferStarted || transferEnded) return false
        transferEnded = true
        return !cancelled && state.transferred()
    }

    @Synchronized fun applied(): Boolean = !cancelled && secondApply &&
        state.applied(LocalInstallState.Phase.APPLYING_TRANSFERRED)

    @Synchronized fun cancel() { cancelled = true; state.fail() }

    @Synchronized fun stopResponse(code: Int, identity: String?) {
        if (cancelled && code == 20003 && identity == taskId) stopped = true
    }

    /** 停止确认之外，还必须等原生排队续接退出，防止清理后重新写缓存或读文件。 */
    @Synchronized fun canRelease(): Boolean = !payloadRunning && !signaturePending && !signatureRunning &&
        !continuationPending && !continuationRunning && !transferPending && !transferDispatchRunning &&
        (!firstApply || transferEnded || stopped)
}
