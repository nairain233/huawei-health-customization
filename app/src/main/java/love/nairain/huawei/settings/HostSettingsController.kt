package love.nairain.huawei.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import love.nairain.huawei.app.ServiceCatalog
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.SettingsCatalog
import java.util.UUID
import java.util.concurrent.Executors

internal enum class HostSettingsNotice { LOADING, READY, SAVING, SAVED, SCHEDULED, FAILED, UNAVAILABLE, UNCERTAIN }
internal data class HostSettingsState(
    val configuration: HostConfiguration = HostConfiguration(),
    val catalog: ServiceCatalog = ServiceCatalog(),
    val writable: Boolean = false,
    val notice: HostSettingsNotice = HostSettingsNotice.LOADING,
) {
    val canRestart: Boolean get() = writable && notice in setOf(
        HostSettingsNotice.READY, HostSettingsNotice.SAVED, HostSettingsNotice.SCHEDULED)
}

/** 宿主进程唯一协调器；窗口只订阅状态，关闭窗口不会中断保存。 */
internal class HostSettingsController(application: Context, private val diagnostic: (String) -> Unit) : HostSettingsCommands {
    // attach 回调早于 LoadedApk 发布 mApplication，不能假定 applicationContext 已可用。
    private val app = if (application is android.app.Application) application else application.applicationContext ?: application
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "HostConfiguration").apply { isDaemon = true } }
    private val store by lazy { HostConfigStore(AtomicHostConfigStorage(app)) }
    private var listener: ((HostSettingsState) -> Unit)? = null
    private var state = HostSettingsState()
    private var startup: HostConfiguration? = null
    private var busy = true
    override val writable: Boolean get() = listener != null && state.writable && !busy
    val saving: Boolean get() = state.notice == HostSettingsNotice.SAVING
    val canRestart: Boolean get() = listener != null && startup != null && !busy && state.canRestart

    /** 在 attach 后异步初始化；业务只取得这一次启动快照。 */
    fun initialize(ready: (HostConfiguration) -> Unit) {
        worker.execute {
            var loaded: HostConfiguration? = null
            val catalog = runCatching { ServiceCatalog.load(app.packageManager) }.getOrDefault(ServiceCatalog())
            runCatching { store.load() }.onSuccess { loaded = it }
                .onFailure { diagnostic("Local configuration load: ${it.javaClass.simpleName}") }
            main.post {
                startup = loaded ?: HostConfiguration()
                busy = false
                state = HostSettingsState(loaded ?: state.configuration, catalog, loaded != null,
                    if (loaded == null) HostSettingsNotice.UNAVAILABLE else HostSettingsNotice.READY)
                publish()
                runCatching { ready(requireNotNull(startup)) }
                    .onFailure { diagnostic("Local runtime initialization: " + it.javaClass.simpleName) }
            }
        }
    }
    fun start(observer: (HostSettingsState) -> Unit): AutoCloseable {
        listener = observer
        publish()
        // 旧窗口的延迟销毁不能取消新窗口的订阅。
        return AutoCloseable { if (listener === observer) listener = null }
    }
    fun stop() { listener = null }
    override fun refresh() {
        if (busy || startup == null) return
        busy = true
        state = state.copy(writable = false, notice = HostSettingsNotice.LOADING); publish()
        worker.execute {
            val loaded = runCatching { store.load() }.onFailure { diagnostic("Local configuration reload: ${it.javaClass.simpleName}") }.getOrNull()
            val catalog = runCatching { ServiceCatalog.load(app.packageManager) }.getOrDefault(state.catalog)
            main.post {
                busy = false
                state = state.copy(configuration = loaded ?: state.configuration, catalog = catalog, writable = loaded != null,
                    notice = if (loaded == null) HostSettingsNotice.UNAVAILABLE else HostSettingsNotice.READY)
                publish()
            }
        }
    }
    override fun saveLayout(key: String, value: Boolean) {
        if (key !in SettingsCatalog.defaults) return
        mutate { it.copy(layout = SettingsCatalog.normalize(it.layout + (key to value))) }
    }
    override fun saveServices(config: ServiceBlockConfig) = mutate { it.copy(services = config) }
    override fun scheduleRescan() = mutate(scheduled = true) { it.copy(scanRequest = UUID.randomUUID().toString()) }
    private fun mutate(scheduled: Boolean = false, change: (HostConfiguration) -> HostConfiguration) {
        if (!writable) return
        val next = runCatching { change(state.configuration).normalized() }.getOrNull() ?: return
        if (next == state.configuration) return
        busy = true
        state = state.copy(writable = false, notice = HostSettingsNotice.SAVING); publish()
        worker.execute {
            val result = runCatching { store.save(next) }.getOrDefault(HostSaveResult.UNAVAILABLE)
            val confirmed = store.confirmed
            val available = store.writable
            main.post {
                busy = false
                state = state.copy(configuration = confirmed ?: state.configuration, writable = available,
                    notice = when (result) {
                        HostSaveResult.SAVED -> if (scheduled) HostSettingsNotice.SCHEDULED else HostSettingsNotice.SAVED
                        HostSaveResult.RESTORED -> HostSettingsNotice.FAILED
                        HostSaveResult.UNCERTAIN -> HostSettingsNotice.UNCERTAIN
                        HostSaveResult.UNAVAILABLE -> HostSettingsNotice.UNAVAILABLE
                    })
                publish()
            }
        }
    }
    private fun publish() { listener?.invoke(state) }
}
