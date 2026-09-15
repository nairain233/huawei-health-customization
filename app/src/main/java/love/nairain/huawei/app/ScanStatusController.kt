package love.nairain.huawei.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.HookInstallPolicy
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.scan.ScanReport
import love.nairain.huawei.scan.ScanRequestOutcome
import love.nairain.huawei.scan.ScanRequests
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 主线程拥有 UI 状态；工作线程只计算快照与提交预约，所有结果按会话接纳。 */
internal class ScanStatusController(
    private val readReport: () -> ScanUiState,
    private val main: Executor,
    private val workerFactory: () -> ScheduledExecutorService = { Executors.newSingleThreadScheduledExecutor() },
    private val update: (ScanUiState) -> Unit,
) {
    constructor(context: Context, update: (ScanUiState) -> Unit) : this(
        reportReader(context.applicationContext), mainDispatcher(), update = update,
    )

    private class Binding(val identity: Any, val preferences: () -> SharedPreferences)
    private class Session(val binding: Binding?)
    private class Save(val session: Session) {
        // 0：排队；1：已开始；2：取消。停止只释放尚未开始的操作。
        val phase = AtomicInteger(0)
    }
    private data class Read(val state: ScanUiState, val preferences: SharedPreferences?)

    private var binding: Binding? = null
    @Volatile private var session: Session? = null
    private var worker: ScheduledExecutorService? = null
    private var revision = 0L
    private var state = ScanUiState()
    private var activeSave: Save? = null
    private var uncertainBinding: Binding? = null
    private var failedBinding: Binding? = null

    fun bind(bound: XposedService?) = bindSource(bound) {
        requireNotNull(bound).getRemotePreferences(SettingsKeys.GROUP)
    }

    internal fun bindSource(identity: Any?, preferences: () -> SharedPreferences) = main.execute {
        if (binding?.identity === identity) return@execute
        binding = identity?.let { Binding(it, preferences) }
        failedBinding = null
        if (session != null) {
            session = Session(binding)
            revision++
            cancelQueuedSave()
            publish(state.copy(writable = false, saving = false, pending = false,
                saveFailed = false, requestUncertain = uncertainBinding != null))
            refresh()
        }
    }

    fun start() = main.execute {
        if (session != null) return@execute
        val current = Session(binding)
        session = current
        revision++
        publish(state.copy(writable = false, saving = false, pending = false,
            saveFailed = binding != null && failedBinding === binding,
            requestUncertain = uncertainBinding != null))
        try {
            val executor = workerFactory()
            worker = executor
            // 轮询读取当前会话，换绑后无需重建定时器；停止再启动由执行器身份隔离。
            executor.scheduleWithFixedDelay({
                main.execute { if (worker === executor && session != null) refresh() }
            }, 2, 2, TimeUnit.SECONDS)
            refresh()
        } catch (_: RejectedExecutionException) {
            worker?.shutdownNow()
            worker = null
            publish(state.copy(writable = false, saving = false, saveFailed = true))
            session = null
        }
    }

    fun stop() = main.execute {
        session = null
        revision++
        cancelQueuedSave()
        worker?.shutdownNow()
        worker = null
    }

    private fun cancelQueuedSave() {
        activeSave?.let { if (it.phase.compareAndSet(0, 2)) activeSave = null }
    }

    private fun publish(next: ScanUiState) {
        state = next
        if (session != null) update(next)
    }

    private fun read(current: Session): Read {
        val report = readReport()
        if (session !== current) return Read(report, null)
        var preferences: SharedPreferences? = null
        val request = try {
            val prefs = current.binding?.preferences?.invoke()
            val value = prefs?.getString(ScanProtocol.REQUEST, "").orEmpty()
            preferences = prefs
            value
        } catch (_: RuntimeException) { "" }
        return Read(report.copy(writable = preferences != null,
            pending = request.isNotEmpty() && (report.report?.request != request ||
                report.report.phase !in setOf("complete", "running"))), preferences)
    }

    private fun refresh() {
        val current = session ?: return
        if (activeSave != null) return
        val readRevision = ++revision
        submit(current, onRejected = {
            publish(state.copy(writable = false, saving = false))
        }) {
            val result = read(current)
            main.execute {
                if (session !== current || revision != readRevision || activeSave != null) return@execute
                // 新绑定也须成功读取后才能解除不确定状态，普通轮询不能自证回滚成功。
                if (current.binding != null && uncertainBinding !== current.binding && result.state.writable) {
                    uncertainBinding = null
                }
                val uncertain = uncertainBinding != null
                val failed = current.binding != null && failedBinding === current.binding
                publish(result.state.copy(requestUncertain = uncertain, saveFailed = failed,
                    pending = result.state.pending && !uncertain && !failed))
            }
        }
    }

    fun request() = main.execute {
        val current = session ?: return@execute
        if (!state.canRequest || activeSave != null) return@execute
        val operation = Save(current)
        activeSave = operation
        revision++
        publish(state.copy(saving = true, saveFailed = false))
        submit(current, onRejected = {
            activeSave = null
            publish(state.copy(saving = false, saveFailed = true))
        }) {
            if (!operation.phase.compareAndSet(0, 1)) return@submit
            var outcome: ScanRequestOutcome? = null
            try {
                val fresh = read(current)
                if (session === current && fresh.state.canRequest && fresh.preferences != null) {
                    // 无法撤销已发出的 IPC；提交和回滚始终使用同一份 preferences。
                    outcome = ScanRequests.save(fresh.preferences, UUID.randomUUID().toString()) {
                        session === current
                    }
                } else if (session === current && fresh.preferences == null) {
                    outcome = ScanRequestOutcome.RESTORED
                }
            } finally {
                val completed = outcome
                main.execute { finish(operation, completed) }
            }
        }
    }

    private fun finish(operation: Save, outcome: ScanRequestOutcome?) {
        if (activeSave !== operation) return
        activeSave = null
        val origin = operation.session
        // 同一绑定即使已停止也保留写入事实，但不向新会话发布旧操作结果。
        if (outcome == ScanRequestOutcome.UNCERTAIN) uncertainBinding = origin.binding
        if (origin.binding != null && origin.binding === binding) {
            if (outcome == ScanRequestOutcome.RESTORED && session === origin) failedBinding = binding
            if (outcome == ScanRequestOutcome.SUCCESS) failedBinding = null
        }
        if (session === origin) {
            publish(state.copy(saving = false, writable = false,
                saveFailed = outcome == ScanRequestOutcome.RESTORED,
                requestUncertain = uncertainBinding != null))
        }
        // 包括旧会话完成的情况：重新读取，绝不直接发布旧会话的成功或 pending。
        refresh()
    }

    private fun submit(current: Session, onRejected: () -> Unit, action: () -> Unit) {
        try {
            val executor = worker ?: throw RejectedExecutionException()
            executor.execute { if (session === current) action() }
        } catch (_: RejectedExecutionException) {
            if (session === current) onRejected()
        }
    }

    companion object {
        private fun mainDispatcher(): Executor {
            val handler = Handler(Looper.getMainLooper())
            return Executor { task ->
                if (Looper.myLooper() == Looper.getMainLooper()) task.run() else handler.post(task)
            }
        }

        @Suppress("DEPRECATION")
        private fun reportReader(app: Context): () -> ScanUiState = {
            val report = runCatching {
                app.getSharedPreferences(ScanProtocol.STORE, 0).getString("report", null)?.let(ScanReport::decode)
            }.getOrNull()
            val target = runCatching {
                app.packageManager.getPackageInfo(HookInstallPolicy.TARGET_PACKAGE, 0)
            }.getOrNull()
            ScanUiState(report = report,
                expired = report != null && (target == null || report.versionCode != target.longVersionCode ||
                    report.updated != target.lastUpdateTime || report.rules != ScanProtocol.RULES),
                uncertain = report?.phase == "running" && System.currentTimeMillis() - report.time > ScanProtocol.STALE_MS)
        }
    }
}
