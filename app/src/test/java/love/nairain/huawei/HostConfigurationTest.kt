package love.nairain.huawei

import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServicePreset
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.settings.HostConfiguration
import love.nairain.huawei.settings.HostConfigStorage
import love.nairain.huawei.settings.HostConfigStore
import love.nairain.huawei.settings.HostSaveResult
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

class HostConfigurationTest {
    private class Memory(var raw: String? = null) : HostConfigStorage {
        var writes = 0
        var writeAction: (String) -> Unit = { raw = it }
        override fun read() = raw
        override fun write(raw: String) { writes++; writeAction(raw) }
    }

    @Test fun newStoreStartsWithSafeDefaultsAndPersistsAcrossInstances() {
        val disk = Memory()
        val first = HostConfigStore(disk).load()
        assertEquals(HostConfiguration(), first)
        assertFalse(first.layout.getValue(SettingsKeys.ENABLED))
        assertFalse(first.layout.getValue(SettingsKeys.LOCAL_WATCH_FACE))
        assertFalse(first.services.enabled)
        assertEquals("", first.scanRequest)
        assertEquals(first, HostConfigStore(disk).load())
        assertEquals(1, disk.writes)
    }

    @Test fun layoutServiceAndRescanSaveAsOneConfirmedSnapshot() {
        val disk = Memory()
        val store = HostConfigStore(disk)
        val startup = store.load()
        val service = ServiceBlockConfig(true, presets = setOf(ServicePreset.APP_UPDATE.id))
        val next = startup.copy(layout = startup.layout + (SettingsKeys.HEALTH_SEARCH to true),
            services = service, scanRequest = UUID.randomUUID().toString())
        assertEquals(HostSaveResult.SAVED, store.save(next))
        assertEquals(next, store.confirmed)
        assertEquals(next, HostConfigStore(disk).load())
        assertFalse(startup.layout.getValue(SettingsKeys.HEALTH_SEARCH))
        assertEquals(ServiceBlockConfig(), startup.services)
    }

    @Test fun failedWriteRestoresExactPreviousFileAndKeepsConfirmedValues() {
        val disk = Memory()
        val store = HostConfigStore(disk)
        val previous = store.load()
        val original = disk.raw
        var attempts = 0
        disk.writeAction = {
            disk.raw = it
            if (++attempts == 1) throw IOException("injected")
        }
        val next = previous.copy(services = ServiceBlockConfig(enabled = true))
        assertEquals(HostSaveResult.RESTORED, store.save(next))
        assertEquals(original, disk.raw)
        assertEquals(previous, store.confirmed)
        assertTrue(store.writable)
        assertEquals(2, attempts)
    }

    @Test fun unconfirmedRecoveryPausesUntilExplicitSuccessfulReadAndNeverReplays() {
        val disk = Memory()
        val store = HostConfigStore(disk)
        val previous = store.load()
        val original = requireNotNull(disk.raw)
        disk.writeAction = { disk.raw = "damaged"; throw IOException("injected") }
        val next = previous.copy(scanRequest = UUID.randomUUID().toString())
        assertEquals(HostSaveResult.UNCERTAIN, store.save(next))
        assertFalse(store.writable)
        val count = disk.writes
        assertEquals(HostSaveResult.UNAVAILABLE, store.save(next))
        assertEquals(count, disk.writes)
        assertThrows(Exception::class.java) { store.load() }
        assertFalse(store.writable)
        disk.raw = original
        disk.writeAction = { disk.raw = it }
        assertEquals(previous, store.load())
        assertTrue(store.writable)
        assertEquals("", store.confirmed?.scanRequest)
        assertEquals(count, disk.writes)
    }

    @Test fun corruptOrFutureConfigurationIsPreservedWithoutDefaultsOverwrite() {
        val default = JSONObject(HostConfiguration().encode())
        val invalid = listOf("{", JSONObject(default.toString()).put(SettingsKeys.SCHEMA_VERSION, 999).toString(),
            JSONObject(default.toString()).put("layout", JSONObject().put(ServiceBlockConfig.ENABLED, "true")).toString())
        invalid.forEach { raw ->
            val disk = Memory(raw)
            val store = HostConfigStore(disk)
            assertThrows(Exception::class.java) { store.load() }
            assertEquals(raw, disk.raw)
            assertFalse(store.writable)
            assertEquals(0, disk.writes)
        }
    }

    @Test fun diskChangesAreNotOverwrittenByStaleConfirmedState() {
        val disk = Memory()
        val store = HostConfigStore(disk)
        val previous = store.load()
        disk.raw = previous.copy(services = ServiceBlockConfig(true)).encode()
        val changed = disk.raw
        assertEquals(HostSaveResult.UNAVAILABLE,
            store.save(previous.copy(layout = previous.layout + (SettingsKeys.ENABLED to true))))
        assertEquals(changed, disk.raw)
        assertFalse(store.writable)
    }

    @Test fun malformedServicesCannotExpandToForeignComponentsOrUnknownGroups() {
        val foreign = HostConfiguration(services = ServiceBlockConfig(components = setOf("android/android.Service")))
        val unknown = HostConfiguration(services = ServiceBlockConfig(presets = setOf("all")))
        listOf(foreign, unknown).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.normalized() }
        }
        val raw = JSONObject(HostConfiguration().encode())
        raw.getJSONObject("services").put(ServiceBlockConfig.ENABLED, "true")
        assertThrows(IllegalArgumentException::class.java) { HostConfiguration.decode(raw.toString()) }
    }

    @Test fun backupRouteNormalizationCoversBothMineEntryFailuresAndAllTabsHidden() {
        val base = SettingsCatalog.defaults + (SettingsKeys.BOTTOM_HEALTH to true)
        assertTrue(SettingsCatalog.normalize(base).getValue(SettingsKeys.BOTTOM_HEALTH))
        listOf(SettingsKeys.BOTTOM_MINE, SettingsKeys.MINE_SETTINGS).forEach { key ->
            val guarded = SettingsCatalog.normalize(base + (key to true))
            assertFalse(guarded.getValue(SettingsKeys.BOTTOM_HEALTH))
            assertTrue(guarded.getValue(key))
            assertFalse(SettingsCatalog.normalizeWrite(SettingsKeys.BOTTOM_HEALTH, true, guarded).getValue(SettingsKeys.BOTTOM_HEALTH))
        }
        assertFalse(SettingsCatalog.normalize(base + SettingsCatalog.bottomTabKeys.associateWith { true })
            .getValue(SettingsKeys.BOTTOM_HEALTH))
    }

    @Test fun catalogHasNoDesktopOrMoreMenuChoicesAndGroupsRemainUnique() {
        assertFalse("hide_launcher_icon" in SettingsCatalog.defaults)
        assertFalse("hide.health.more_menu" in SettingsCatalog.defaults)
        assertEquals(SettingsCatalog.all.size, SettingsCatalog.all.map { it.key }.distinct().size)
        SettingsCategory.entries.forEach {
            val flattened = SettingsCatalog.groupsFor(it).flatMap { group -> group.settings } +
                if (it == SettingsCategory.SPORT) SettingsCatalog.sportQuickEntries else emptyList()
            assertEquals(SettingsCatalog.settingsFor(it), flattened)
        }
    }

    @Test fun readFailureDoesNotAttemptDefaultWrite() {
        var writes = 0
        val store = HostConfigStore(object : HostConfigStorage {
            override fun read(): String = throw IOException("injected")
            override fun write(raw: String) { writes++ }
        })
        assertThrows(IOException::class.java) { store.load() }
        assertEquals(0, writes)
        assertFalse(store.writable)
    }
}
