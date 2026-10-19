package love.nairain.huawei

import love.nairain.huawei.app.ServiceCatalog
import love.nairain.huawei.app.ServiceItem
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServicePreset
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.scan.ScanReport
import love.nairain.huawei.settings.*
import org.junit.Assert.*
import org.junit.Test

/** 使用独立命令记录器验证写入边界，不启用宿主真实服务屏蔽。 */
class HostSettingsSessionTest {
    private class Commands : HostSettingsCommands {
        override var writable = true
        val services = mutableListOf<ServiceBlockConfig>()
        val layout = mutableListOf<Pair<String, Boolean>>()
        var refreshes = 0
        var scans = 0
        override fun refresh() { refreshes++ }
        override fun saveLayout(key: String, value: Boolean) { layout += key to value }
        override fun saveServices(config: ServiceBlockConfig) { services += config }
        override fun scheduleRescan() { scans++ }
    }
    private class Fixture {
        val commands = Commands()
        val page = HostSettingsPageState()
        val effects = mutableListOf<HostSettingsAction>()
        val session = HostSettingsSession(page, commands, {}, effects::add)
        fun accept(config: HostConfiguration = HostConfiguration(), notice: HostSettingsNotice = HostSettingsNotice.READY) {
            val catalog = ServiceCatalog(ServicePreset.entries.flatMap { it.components }.distinct().map { ServiceItem(it) }, true)
            session.accept(HostSettingsState(config, catalog, notice == HostSettingsNotice.READY || notice == HostSettingsNotice.SAVED, notice))
        }
        init { session.resume(); accept(); session.restartState(true, false) }
    }

    @Test fun serviceConfirmationUsesLatestSnapshotAndDoesNotOptimisticallySelect() {
        val f = Fixture()
        val preset = ServicePreset.DEVICE_CORE
        f.session.dispatch(HostSettingsAction.Preset(preset.id, true))
        assertEquals(preset, f.session.model().pendingPreset)
        assertTrue(f.commands.services.isEmpty())
        assertFalse(preset.id in f.session.model().services.presets)
        val component = "com.huawei.health/com.huawei.health.CustomService"
        val later = ServiceBlockConfig(enabled = true, components = setOf(component), presets = setOf(ServicePreset.APP_UPDATE.id), debugMode = true)
        f.accept(HostConfiguration(services = later))
        f.session.dispatch(HostSettingsAction.ConfirmPreset)
        val saved = f.commands.services.single()
        assertTrue(saved.enabled)
        assertTrue(saved.debugMode)
        assertEquals(later.components, saved.components)
        assertEquals(later.presets + preset.id, saved.presets)
        assertNull(f.session.model().pendingPreset)
        assertEquals(later, f.session.model().services)
    }

    @Test fun cancelledStoppedAndRepeatedConfirmationsNeverWrite() {
        val f = Fixture()
        val request = HostSettingsAction.Preset(ServicePreset.DEVICE_CORE.id, true)
        f.session.dispatch(request)
        f.session.dispatch(HostSettingsAction.CancelPreset)
        f.session.dispatch(HostSettingsAction.ConfirmPreset)
        f.session.dispatch(request)
        f.session.dispatch(HostSettingsAction.Back)
        assertEquals("home", f.page.route)
        f.session.dispatch(request)
        f.session.pause()
        f.session.resume()
        f.session.dispatch(HostSettingsAction.ConfirmPreset)
        assertTrue(f.commands.services.isEmpty())
        assertTrue(f.effects.isEmpty())
    }

    @Test fun savingFailureAndUncertaintyBlockEditsAndRestart() {
        val f = Fixture()
        for (notice in listOf(HostSettingsNotice.SAVING, HostSettingsNotice.FAILED, HostSettingsNotice.UNCERTAIN)) {
            f.commands.writable = false
            f.accept(notice = notice)
            f.session.dispatch(HostSettingsAction.Layout(SettingsKeys.ENABLED, true))
            f.session.dispatch(HostSettingsAction.ServiceEnabled(true))
            f.session.dispatch(HostSettingsAction.Restart)
            assertFalse(f.session.model().restartAvailable)
        }
        assertTrue(f.commands.layout.isEmpty())
        assertTrue(f.commands.services.isEmpty())
        assertTrue(f.effects.isEmpty())
        f.session.dispatch(HostSettingsAction.Refresh)
        assertEquals(1, f.commands.refreshes)
        assertTrue(f.commands.services.isEmpty())
    }

    @Test fun confirmationCannotWriteWhileUnavailableAndRetryDoesNotReplay() {
        val f = Fixture()
        f.session.dispatch(HostSettingsAction.Preset(ServicePreset.DEVICE_CORE.id, true))
        f.commands.writable = false
        f.session.dispatch(HostSettingsAction.ConfirmPreset)
        assertNotNull(f.session.model().pendingPreset)
        f.session.pause()
        f.session.resume()
        f.commands.writable = true
        f.session.dispatch(HostSettingsAction.Refresh)
        f.session.dispatch(HostSettingsAction.ConfirmPreset)
        assertTrue(f.commands.services.isEmpty())
    }

    @Test fun draftsAndFiltersAreIndependentOfConfigurationWrites() {
        val f = Fixture()
        f.accept(HostConfiguration(services = ServiceBlockConfig(debugMode = true)))
        f.session.dispatch(HostSettingsAction.Navigate("custom"))
        f.session.dispatch(HostSettingsAction.DraftQuery("service draft"))
        assertEquals("", f.session.model().query)
        f.accept(notice = HostSettingsNotice.SAVED)
        assertEquals("service draft", f.session.model().draftQuery)
        f.session.dispatch(HostSettingsAction.ApplyFilter)
        f.session.dispatch(HostSettingsAction.SelectedOnly(true))
        assertEquals("service draft", f.session.model().query)
        assertTrue(f.session.model().selectedOnly)
        assertEquals(2, f.session.model().filterRevision)
        assertTrue(f.commands.services.isEmpty())
    }

    @Test fun healthRouteAndDebugModeGuardsPreserveConfirmedValues() {
        val f = Fixture()
        val base = HostConfiguration()
        f.accept(base.copy(layout = base.layout + (SettingsKeys.BOTTOM_MINE to true)))
        f.session.dispatch(HostSettingsAction.Layout(SettingsKeys.BOTTOM_HEALTH, true))
        f.session.dispatch(HostSettingsAction.Navigate("custom"))
        f.session.dispatch(HostSettingsAction.Component("com.huawei.health/com.huawei.health.Missing", true))
        assertTrue(f.commands.layout.isEmpty())
        assertTrue(f.commands.services.isEmpty())
        assertEquals("home", f.page.route)
    }

    @Test fun missingSelectedComponentCanBeRemovedButCannotBeAdded() {
        val f = Fixture()
        val component = "com.huawei.health/com.huawei.health.Missing"
        f.accept(HostConfiguration(services = ServiceBlockConfig(components = setOf(component), debugMode = true)))
        f.session.dispatch(HostSettingsAction.Component(component, false))
        assertTrue(f.commands.services.single().components.isEmpty())
        f.accept(HostConfiguration(services = ServiceBlockConfig(debugMode = true)))
        f.session.dispatch(HostSettingsAction.Component(component, true))
        assertEquals(1, f.commands.services.size)
    }

    @Test fun scanRequestsRespectPendingRunningAndStaleReportBoundaries() {
        val f = Fixture()
        f.session.dispatch(HostSettingsAction.Rescan)
        assertEquals(1, f.commands.scans)
        val request = "request"
        f.accept(HostConfiguration(scanRequest = request))
        f.session.dispatch(HostSettingsAction.Rescan)
        assertEquals(1, f.commands.scans)
        val report = ScanReport("pending", "17.0.8.300", 1700008300, 1, request, "run", 1, phase = "complete", time = 10)
        f.session.report(report, false, 20)
        f.session.dispatch(HostSettingsAction.Rescan)
        assertEquals(2, f.commands.scans)
        f.accept()
        f.session.report(report.copy(phase = "running"), false, 20)
        assertFalse(f.session.model().canRescan)
        f.session.report(report.copy(phase = "running"), false, 200_000)
        assertTrue(f.session.model().canRescan)
    }
}
