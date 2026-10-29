package love.nairain.huawei.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import love.nairain.huawei.app.ServiceCatalog
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SportFixedItems
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal enum class HostSettingsNotice { LOADING, READY, SAVING, SAVED, SCHEDULED, FAILED, UNAVAILABLE, UNCERTAIN }
internal data class HostSettingsState(
    val configuration: HostConfiguration = HostConfiguration(),
    val catalog: ServiceCatalog = ServiceCatalog(),
    val writable: Boolean = false,
    val notice: HostSettingsNotice = HostSettingsNotice.LOADING,
    val sportIdentity: String? = null,
    val sportTabs: List<Int> = SportFixedItems.pageIds,
) {
    val canRestart: Boolean get() = writable && notice in setOf(
        HostSettingsNotice.READY, HostSettingsNotice.SAVED, HostSettingsNotice.SCHEDULED)
}

/** 宿主进程唯一协调器；窗口只订阅状态，关闭窗口不会中断保存。 */
internal class HostSettingsController(
    createStore: () -> HostConfigStore,
    private val worker: ScheduledExecutorService,
    private val postMain: (() -> Unit) -> Unit,
    private val loadCatalog: () -> ServiceCatalog,
    private val diagnostic: (String) -> Unit,
    private val trace: (String) -> Unit = {},
) : HostSettingsCommands {
    constructor(application: Context, diagnostic: (String) -> Unit) : this(application, diagnostic, {})

    constructor(application: Context, diagnostic: (String) -> Unit, trace: (String) -> Unit) : this(
        {
            // attach 回调早于 LoadedApk 发布 mApplication，不能假定 applicationContext 已可用。
            val app = if (application is android.app.Application) application else application.applicationContext ?: application
            HostConfigStore(AtomicHostConfigStorage(app))
        },
        Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "HostConfiguration").apply { isDaemon = true } },
        Handler(Looper.getMainLooper()).let { main -> { action -> main.post(action); Unit } },
        { ServiceCatalog.load(application.packageManager) },
        diagnostic,
        trace,
    )

    // 目录访问和 AtomicFile 构造仍在配置工作线程，测试可替换存储及任务队列。
    private val store by lazy(createStore)
    private var listener: ((HostSettingsState) -> Unit)? = null
    private var state = HostSettingsState()
    private var startup: HostConfiguration? = null
    private var initialized = false
    private var startupReady: ((HostConfiguration) -> Unit)? = null
    private var busy = true
    override val writable: Boolean get() = listener != null && state.writable && !busy
    val saving: Boolean get() = state.notice == HostSettingsNotice.SAVING
    val canRestart: Boolean get() = listener != null && startup != null && !busy && state.canRestart

    /** 在 attach 后异步初始化；业务只取得这一次启动快照。 */
    fun initialize(ready: (HostConfiguration) -> Unit) {
        if (initialized) return
        initialized = true
        startupReady = ready
        worker.execute {
            val catalog = runCatching(loadCatalog).getOrDefault(ServiceCatalog())
            loadStartup(catalog, 0)
        }
    }

    /** 仅重试启动时的 I/O 故障；校验失败保留文件，不把默认关闭状态分发给业务。 */
    private fun loadStartup(catalog: ServiceCatalog, attempt: Int) {
        val result = runCatching { store.load() }
        val error = result.exceptionOrNull()
        if (error != null) {
            diagnostic("Local configuration startup failed: ${error.javaClass.simpleName}")
            val delay = when (attempt) { 0 -> 250L; 1 -> 1000L; else -> null }
            if (error is IOException && delay != null) {
                trace("Local configuration startup retry scheduled: ${attempt + 1}")
                worker.schedule(Runnable { loadStartup(catalog, attempt + 1) }, delay, TimeUnit.MILLISECONDS)
                return
            }
        } else trace("Local configuration startup loaded")
        postMain { acceptLoaded(result.getOrNull(), catalog) }
    }

    private fun acceptLoaded(loaded: HostConfiguration?, catalog: ServiceCatalog) {
        state = state.copy(configuration = loaded ?: state.configuration, catalog = catalog, writable = loaded != null,
            notice = if (loaded == null) HostSettingsNotice.UNAVAILABLE else HostSettingsNotice.READY)
        if (loaded != null && startup == null) {
            val ready = startupReady
            // 分发前先占位；回调重入或部分安装后抛错都不能重复安装业务 Hook。
            startup = loaded
            startupReady = null
            trace("Local startup snapshot dispatch started")
            runCatching { requireNotNull(ready)(loaded) }
                .onSuccess { trace("Local startup snapshot dispatched") }
                .onFailure { diagnostic("Local startup snapshot dispatch failed: ${it.javaClass.simpleName}") }
        }
        // 在快照分发后才向页面开放保存与重启，避免观察者抢先结束进程。
        busy = false
        publish()
    }
    fun start(observer: (HostSettingsState) -> Unit): AutoCloseable {
        listener = observer
        publish()
        // 旧窗口的延迟销毁不能取消新窗口的订阅。
        return AutoCloseable { if (listener === observer) listener = null }
    }
    fun stop() { listener = null }
    override fun refresh() {
        if (busy || !initialized) return
        busy = true
        state = state.copy(writable = false, notice = HostSettingsNotice.LOADING); publish()
        worker.execute {
            val loaded = runCatching { store.load() }
                .onSuccess { trace("Local configuration reload loaded") }
                .onFailure { diagnostic("Local configuration reload failed: ${it.javaClass.simpleName}") }.getOrNull()
            val catalog = runCatching(loadCatalog).getOrDefault(state.catalog)
            postMain { acceptLoaded(loaded, catalog) }
        }
    }
    override fun saveLayout(key: String, value: Boolean) {
        if (key !in SettingsCatalog.defaults) return
        mutate { it.copy(layout = SettingsCatalog.normalize(it.layout + (key to value))) }
    }
    override fun saveSportTab(id: Int, hidden: Boolean) {
        if (id !in SportFixedItems.pageIds) return
        if (state.configuration.sport.hiddenTabs.isNotEmpty() && state.sportIdentity == null) return
        if (hidden && id in state.sportTabs &&
            !state.configuration.sport.canHideTab(state.sportIdentity.orEmpty(), id, state.sportTabs)) return
        mutate { it.copy(sport = it.sport.copy(overrides = it.sport.overrides + ("tab/$id" to hidden))) }
    }
    override fun saveSportItem(id: String, hidden: Boolean) {
        val item = SportFixedItems.item(id) ?: return
        if (item.parentKey?.let { state.configuration.sport.hidden(
            love.nairain.huawei.config.SportItem.fixed(item.pageId, it), state.configuration.layout) } == true) return
        mutate { it.copy(sport = it.sport.copy(overrides = it.sport.overrides + (id to hidden))) }
    }
    /** 只接收扫描身份和当前导航 ID，不采集内容、不写发现目录。 */
    fun identifySport(identity: String) { postMain { state = state.copy(sportIdentity = identity); publish() } }
    fun sportTabsChanged(ids: List<Int>) { postMain { state = state.copy(sportTabs = ids.toList()); publish() } }
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
            postMain {
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
