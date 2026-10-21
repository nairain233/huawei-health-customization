package love.nairain.huawei

import love.nairain.huawei.config.*
import love.nairain.huawei.settings.HostConfiguration
import love.nairain.huawei.settings.HostConfigStorage
import love.nairain.huawei.settings.HostConfigStore
import love.nairain.huawei.settings.HostSaveResult
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SportSimplificationTest {
    private val apk = "a".repeat(64)
    private val other = "b".repeat(64)
    private fun rules(vararg ids: Int) = SportSimplificationConfig(ids.map { "$apk/$it" }.toSet())

    @Test fun tabFilteringPreservesHostOrderAndReevaluatesAfterRemoval() {
        assertEquals(listOf(258, 2), SportTabPolicy.visible(listOf(258, 137, 2), { it }, rules(137), apk))
        assertEquals(listOf(2, 258), SportTabPolicy.visible(listOf(2, 137, 258), { it }, rules(137), apk))
        assertEquals(listOf(137), SportTabPolicy.visible(listOf(137), { it }, rules(137), apk))
    }
    @Test fun allHiddenRetainsFirstWithoutRewritingUserChoices() {
        val config = rules(2, 258)
        assertEquals(listOf(258), SportTabPolicy.visible(listOf(258, 2), { it }, config, apk))
        assertEquals(setOf("$apk/2", "$apk/258"), config.hiddenTabs)
        assertEquals(258, SportTabPolicy.target(2, listOf(258)))
        assertNull(SportTabPolicy.target(2, emptyList()))
    }
    @Test fun unknownDuplicateAndChangedApkIdentitiesFailOpen() {
        assertEquals(listOf(2, null), SportTabPolicy.visible(listOf(2, null), { it }, rules(2), apk))
        assertEquals(listOf(2, 2, 258), SportTabPolicy.visible(listOf(2, 2, 258), { it }, rules(2), apk))
        assertEquals(listOf(2, 258), SportTabPolicy.visible(listOf(2, 258), { it }, rules(2), other))
    }
    @Test fun fixedCatalogHasNinePagesAndOnlyObservedEntries() {
        assertEquals(listOf(500001, 2, 258, 137, 10001, 257, 259, 286, 283), SportFixedItems.pageIds)
        assertEquals(15, SportFixedItems.items(500001).count { it.kind == SportItemKind.ENTRY })
        assertEquals(5, SportFixedItems.items(258).count { it.kind == SportItemKind.ENTRY })
        assertTrue(SportFixedItems.items(137).none { it.kind == SportItemKind.ENTRY })
        assertTrue(SportFixedItems.items(264).isEmpty())
        val all = SportFixedItems.pageIds.flatMap(SportFixedItems::items)
        assertEquals(all.size, all.map { it.id }.distinct().size)
        all.forEach { item ->
            assertTrue(item.title != 0)
            item.parentKey?.let { assertNotNull(SportFixedItems.item(SportItem.fixed(item.pageId, it).id)) }
        }
    }
    @Test fun pageOverridesKeepLegacyDefaultsWithoutBleedingAcrossPages() {
        val run = SportItem.fixed(258, SettingsKeys.SPORT_RUN_SUMMARY)
        val walk = SportItem.fixed(257, SettingsKeys.SPORT_RUN_SUMMARY)
        val old = mapOf(SettingsKeys.SPORT_RUN_SUMMARY to true)
        assertTrue(SportSimplificationConfig().hidden(run, old))
        assertFalse(SportSimplificationConfig().hidden(walk, old))
        val config = SportSimplificationConfig(overrides = mapOf(run.id to false, walk.id to true))
        assertFalse(config.hidden(run, old)); assertTrue(config.hidden(walk, old))
    }
    @Test fun staleDynamicSelectionsAreRetainedButCannotExecute() {
        val stale = "$apk/258/section/" + "c".repeat(64)
        val config = SportSimplificationConfig(overrides = mapOf(stale to true))
        assertEquals(config, SportSimplificationConfig.decode(config.encode()))
        assertFalse(config.hidden(SportItem(stale, 258), emptyMap()))
        assertFalse(config.hidden(SportItem.fixed(137, SettingsKeys.SPORT_BANNER), mapOf(SettingsKeys.SPORT_BANNER to true)))
    }
    @Test fun explicitTabDisplayOverridesOldHiddenRuleAndDoesNotNeedBrowsing() {
        val config = rules(258).copy(overrides = mapOf("tab/258" to false, "tab/137" to true))
        assertFalse(config.hidesTab(apk, 258))
        assertTrue(config.hidesTab("", 137))
        assertEquals(listOf(258), SportTabPolicy.visible(listOf(258, 137), { it }, config, ""))
    }
    @Test fun groupHidingPausesChildWithoutDiscardingItsSelection() {
        val child = SportFixedItems.items(500001).first { it.legacyKey == SettingsKeys.SPORT_STRETCH }
        val parent = SportItem.fixed(500001, SettingsKeys.SPORT_QUICK_ENTRIES)
        val choices = SportSimplificationConfig(overrides = mapOf(child.id to true, parent.id to true))
        assertTrue(choices.hidden(child, emptyMap()))
        assertFalse(choices.effectiveHidden(child, emptyMap()))
        val restored = choices.copy(overrides = choices.overrides + (parent.id to false))
        assertTrue(restored.effectiveHidden(child, emptyMap()))
    }
    @Test fun lastAvailableTabCannotBeHiddenEvenWhenUnavailableTabsRemainInHistory() {
        assertFalse(rules().canHideTab(apk, 258, listOf(258)))
        assertTrue(rules().canHideTab(apk, 258, listOf(258, 137)))
        assertFalse(rules(137).canHideTab(apk, 258, listOf(258, 137)))
        assertFalse(rules().canHideTab(apk, 2, listOf(258)))
    }
    @Test fun schema3LoadsExistingChoicesAndWritesSchema4WithoutImportingOldPreferences() {
        val original = HostConfiguration(layout = SettingsCatalog.defaults + (SettingsKeys.SPORT_BANNER to true))
        val old = JSONObject(original.encode()).put(SettingsKeys.SCHEMA_VERSION, 3).also { it.remove("sport") }
        val migrated = HostConfiguration.decode(old.toString())
        assertTrue(migrated.layout.getValue(SettingsKeys.SPORT_BANNER))
        assertEquals(SportSimplificationConfig(), migrated.sport)
        assertEquals(4, JSONObject(migrated.encode()).getInt(SettingsKeys.SCHEMA_VERSION))
        assertEquals(migrated, HostConfiguration.decode(migrated.encode()))
    }
    @Test fun sportWriteFailureRestoresExactSchema3FileAndConfirmedChoices() {
        var raw = JSONObject(HostConfiguration().encode()).put(SettingsKeys.SCHEMA_VERSION, 3).also { it.remove("sport") }.toString()
        val previousRaw = raw
        var fail = false
        val store = HostConfigStore(object : HostConfigStorage {
            override fun read() = raw
            override fun write(value: String) { raw = value; if (fail) { fail = false; throw IOException() } }
        })
        val previous = store.load()
        fail = true
        assertEquals(HostSaveResult.RESTORED, store.save(previous.copy(sport = rules(258))))
        assertEquals(previousRaw, raw)
        assertEquals(previous, store.confirmed)
    }
    @Test fun schema3MissingNewKeysUsesSafeDefaultsWithoutReadingRetiredKeys() {
        val old = JSONObject(HostConfiguration().encode()).put(SettingsKeys.SCHEMA_VERSION, 3)
        old.getJSONObject("layout").remove(SettingsKeys.SPORT_BANNER)
        val loaded = HostConfiguration.decode(old.toString())
        assertFalse(loaded.layout.getValue(SettingsKeys.SPORT_BANNER))
        old.getJSONObject("layout").put("tab_sport", true)
        assertThrows(IllegalArgumentException::class.java) { HostConfiguration.decode(old.toString()) }
    }
    @Test fun configRoundTripRetainsStaleChoicesAndRejectsMalformedTypes() {
        val config = rules(258).copy(overrides = mapOf(SportItem.fixed(258, SettingsKeys.SPORT_BANNER).id to false))
        assertEquals(config, SportSimplificationConfig.decode(config.encode()))
        assertThrows(IllegalArgumentException::class.java) {
            SportSimplificationConfig.decode(config.encode().put("items", JSONObject().put("bad", "true")))
        }
    }
}
