package love.nairain.huawei.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.HookInstallPolicy
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.scan.ScanReport
import love.nairain.huawei.scan.ScanRequests
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 仅前台轮询，报告持久化由 Provider 负责；所有配置 IPC 和磁盘读取在工作线程。 */
internal class ScanStatusController(context: Context, private val update: (ScanUiState) -> Unit) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var worker = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var running = false
    @Volatile private var service: XposedService? = null
    private var state = ScanUiState()

    fun bind(bound: XposedService?) { service = bound }
    fun start() {
        if (running) return
        running = true
        if (worker.isShutdown) worker = Executors.newSingleThreadScheduledExecutor()
        worker.scheduleWithFixedDelay({ refresh() }, 0, 2, TimeUnit.SECONDS)
    }
    fun stop() { running = false; worker.shutdownNow() }

    private fun emit(next: ScanUiState) {
        state = next
        main.post { if (running) update(next) }
    }

    @Suppress("DEPRECATION")
    private fun refresh() {
        if (!running) return
        val report = runCatching {
            app.getSharedPreferences(ScanProtocol.STORE, 0).getString("report", null)?.let(ScanReport::decode)
        }.getOrNull()
        val target = runCatching { app.packageManager.getPackageInfo(HookInstallPolicy.TARGET_PACKAGE, 0) }.getOrNull()
        val prefs = runCatching { service?.getRemotePreferences(SettingsKeys.GROUP) }.getOrNull()
        val request = runCatching { prefs?.getString(ScanProtocol.REQUEST, "").orEmpty() }.getOrDefault("")
        emit(state.copy(report = report, writable = prefs != null,
            pending = !state.saveFailed && request.isNotEmpty() && (report?.request != request || report.phase !in setOf("complete", "running")),
            expired = report != null && (target == null || report.versionCode != target.longVersionCode ||
                report.updated != target.lastUpdateTime || report.rules != ScanProtocol.RULES),
            uncertain = report?.phase == "running" && System.currentTimeMillis() - report.time > ScanProtocol.STALE_MS))
    }

    fun request() {
        if (!running) return
        worker.execute {
            refresh()
            if (!state.canRequest) return@execute
            emit(state.copy(saving = true, saveFailed = false))
            val success = runCatching {
                val prefs = requireNotNull(service).getRemotePreferences(SettingsKeys.GROUP)
                check(ScanRequests.save(prefs, UUID.randomUUID().toString()))
            }.isSuccess
            emit(state.copy(saving = false, saveFailed = !success))
            refresh()
        }
    }
}
