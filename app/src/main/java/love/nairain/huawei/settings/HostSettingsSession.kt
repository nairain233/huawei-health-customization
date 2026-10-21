package love.nairain.huawei.settings

import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServicePreset
import love.nairain.huawei.config.ServiceSection
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.scan.ScanReport

/** 窗口仅发出既有配置命令，便于独立验证服务确认不会覆盖后来的配置。 */
internal interface HostSettingsCommands {
    val writable: Boolean
    fun refresh()
    fun saveLayout(key: String, value: Boolean)
    fun saveServices(config: ServiceBlockConfig)
    fun scheduleRescan()
    fun saveSportTab(id: Int, hidden: Boolean) {}
    fun saveSportItem(id: String, hidden: Boolean) {}
}

internal data class HostSettingsUiState(
    val route: String = "home",
    val settings: HostSettingsState = HostSettingsState(),
    val writable: Boolean = false,
    val restartAvailable: Boolean = false,
    val watchFaceBusy: Boolean = false,
    val report: ScanReport? = null,
    val expired: Boolean = false,
    val scanTimedOut: Boolean = false,
    val draftQuery: String = "",
    val query: String = "",
    val selectedOnly: Boolean = false,
    val filterRevision: Int = 0,
    val pendingPreset: ServicePreset? = null,
) {
    val configuration get() = settings.configuration
    val services get() = configuration.services
    val declared get() = settings.catalog.services.map { it.component }.toSet()
    val needsHealthRoute get() = configuration.layout[SettingsKeys.BOTTOM_MINE] == true
    val scanPending get() = configuration.scanRequest.let {
        if (it.isEmpty()) settings.notice == HostSettingsNotice.SCHEDULED else report?.request != it || report.phase != "complete"
    }
    val canRescan get() = writable && !scanPending && (report?.phase != "running" || scanTimedOut || expired)
    fun layoutEditable(key: String) = writable && (key != SettingsKeys.BOTTOM_HEALTH || !needsHealthRoute)
    fun presetEditable(preset: ServicePreset) = writable &&
        (preset.components.any(declared::contains) || preset.id in services.presets)
    fun componentEditable(component: String) = writable && services.debugMode &&
        (component in declared || component in services.components)
}

internal sealed interface HostSettingsAction {
    data class Navigate(val route: String) : HostSettingsAction
    data object Back : HostSettingsAction
    data object Close : HostSettingsAction
    data object Restart : HostSettingsAction
    data object Refresh : HostSettingsAction
    data object Rescan : HostSettingsAction
    data class Layout(val key: String, val value: Boolean) : HostSettingsAction
    data class SportTab(val id: Int, val hidden: Boolean) : HostSettingsAction
    data class SportItem(val id: String, val hidden: Boolean) : HostSettingsAction
    data class ServiceEnabled(val value: Boolean) : HostSettingsAction
    data class DebugMode(val value: Boolean) : HostSettingsAction
    data class Preset(val id: String, val value: Boolean) : HostSettingsAction
    data class ClearLegacy(val id: String) : HostSettingsAction
    data object ConfirmPreset : HostSettingsAction
    data object CancelPreset : HostSettingsAction
    data class Component(val component: String, val value: Boolean) : HostSettingsAction
    data object ClearCustom : HostSettingsAction
    data class DraftQuery(val value: String) : HostSettingsAction
    data object ApplyFilter : HostSettingsAction
    data class SelectedOnly(val value: Boolean) : HostSettingsAction
    data class OpenLink(val url: String) : HostSettingsAction
}

/** 配置快照只由订阅更新；界面操作不做乐观写入，也不跨重试重放命令。 */
internal class HostSettingsSession(
    private val page: HostSettingsPageState,
    private val commands: HostSettingsCommands,
    private val changed: () -> Unit,
    private val effect: (HostSettingsAction) -> Unit,
) {
    private var active = false
    private var snapshot = HostSettingsState()
    private var report: ScanReport? = null
    private var expired = false
    private var timedOut = false
    private var restartAvailable = false
    private var watchFaceBusy = false
    private var pendingPreset: ServicePreset? = null
    private var filterRevision = 0

    fun model() = HostSettingsUiState(page.route, snapshot, active && commands.writable,
        active && restartAvailable && snapshot.canRestart && commands.writable, watchFaceBusy, report, expired, timedOut,
        page.draftQuery, page.query, page.selectedOnly, filterRevision, pendingPreset)

    fun accept(value: HostSettingsState) { snapshot = value; changed() }
    fun restartState(available: Boolean, busy: Boolean) {
        restartAvailable = available
        watchFaceBusy = busy
        changed()
    }
    fun report(value: ScanReport?, invalid: Boolean, now: Long) {
        report = value
        expired = invalid
        timedOut = value?.phase == "running" && now - value.time > ScanProtocol.STALE_MS
        changed()
    }
    fun resume() { active = true; changed() }
    fun pause() { active = false; pendingPreset = null; changed() }

    fun dispatch(action: HostSettingsAction) {
        if (!active) return
        val current = model()
        if (pendingPreset != null && action !in setOf(HostSettingsAction.ConfirmPreset,
                HostSettingsAction.CancelPreset, HostSettingsAction.Back, HostSettingsAction.Close)) return
        when (action) {
            is HostSettingsAction.Navigate -> {
                if (!HostSettingsPageState.validRoute(action.route) || (action.route == "custom" && !current.services.debugMode)) return
                pendingPreset = null
                page.route = action.route
                effect(action)
            }
            HostSettingsAction.Back -> {
                if (pendingPreset != null) pendingPreset = null
                else {
                    val parent = HostSettingsPageState.parent(page.route)
                    if (parent == "close") effect(HostSettingsAction.Close)
                    else { page.route = parent; effect(HostSettingsAction.Navigate(parent)) }
                }
            }
            HostSettingsAction.CancelPreset -> pendingPreset = null
            HostSettingsAction.ConfirmPreset -> {
                val preset = pendingPreset ?: return
                // 使用确认时的最新快照，不能复用打开弹窗时捕获的配置。
                if (!current.presetEditable(preset)) return
                pendingPreset = null
                commands.saveServices(current.services.copy(presets = ServicePreset.changeSelection(current.services.presets, preset, true)))
            }
            is HostSettingsAction.Preset -> {
                val preset = ServicePreset.fromId(action.id) ?: return
                if (!current.presetEditable(preset)) return
                if (action.value && preset.section == ServiceSection.CORE) pendingPreset = preset
                else commands.saveServices(current.services.copy(presets = ServicePreset.changeSelection(current.services.presets, preset, action.value)))
            }
            is HostSettingsAction.ClearLegacy -> {
                val preset = ServicePreset.fromId(action.id) ?: return
                if (current.writable) commands.saveServices(current.services.copy(presets =
                    ServicePreset.changeSelection(current.services.presets, preset, false)))
            }
            is HostSettingsAction.Layout -> if (current.layoutEditable(action.key)) commands.saveLayout(action.key, action.value)
            is HostSettingsAction.SportTab -> if (current.writable) commands.saveSportTab(action.id, action.hidden)
            is HostSettingsAction.SportItem -> if (current.writable) commands.saveSportItem(action.id, action.hidden)
            is HostSettingsAction.ServiceEnabled -> if (current.writable) commands.saveServices(current.services.copy(enabled = action.value))
            is HostSettingsAction.DebugMode -> if (current.writable) commands.saveServices(current.services.copy(debugMode = action.value))
            is HostSettingsAction.Component -> if (current.componentEditable(action.component)) {
                val values = current.services.components
                commands.saveServices(current.services.copy(components = if (action.value) values + action.component else values - action.component))
            }
            HostSettingsAction.ClearCustom -> if (current.writable && current.services.debugMode)
                commands.saveServices(current.services.copy(components = emptySet()))
            HostSettingsAction.Refresh -> commands.refresh()
            HostSettingsAction.Rescan -> if (current.canRescan) commands.scheduleRescan()
            is HostSettingsAction.DraftQuery -> page.draftQuery = action.value
            HostSettingsAction.ApplyFilter -> { page.query = page.draftQuery; filterRevision++ }
            is HostSettingsAction.SelectedOnly -> { page.selectedOnly = action.value; filterRevision++ }
            HostSettingsAction.Restart -> if (current.restartAvailable) effect(action)
            HostSettingsAction.Close -> { pendingPreset = null; effect(action) }
            is HostSettingsAction.OpenLink -> effect(action)
        }
        changed()
    }
}
