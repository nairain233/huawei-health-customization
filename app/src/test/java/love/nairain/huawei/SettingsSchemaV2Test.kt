package love.nairain.huawei

import love.nairain.huawei.config.LayoutConfigStore
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSchemaV2Test {
    @Test
    fun groupedCatalogFlattensWithoutMissingOrDuplicateSettings() {
        SettingsCategory.entries.forEach { category ->
            val groupedSettings = SettingsCatalog.groupsFor(category).flatMap { it.settings }
            assertEquals(SettingsCatalog.settingsFor(category), groupedSettings)
            assertEquals(groupedSettings.size, groupedSettings.map { it.key }.distinct().size)
        }
    }

    @Test
    fun mineOrdersAndAssetsBelongToOtherGroup() {
        val other = SettingsCatalog.mineGroups.single { it.id == "other" }
        assertEquals(R.string.settings_group_other, other.title)
        assertTrue(other.settings.any { it.key == SettingsKeys.MINE_ORDERS })
        assertTrue(other.settings.any { it.key == SettingsKeys.MINE_ASSETS })
    }

    @Test
    fun mineMarketingSettingDefaultsOffInItsOwnGroup() {
        val marketing = SettingsCatalog.mineGroups.single { it.id == "marketing" }
        assertEquals(R.string.settings_group_marketing_content, marketing.title)
        assertEquals(listOf(SettingsKeys.MINE_MARKETING), marketing.settings.map { it.key })
        assertFalse(SettingsCatalog.defaults.getValue(SettingsKeys.MINE_MARKETING))
    }

    @Test
    fun groupsWithoutNaturalLabelsKeepNullTitles() {
        assertNull(SettingsCatalog.mineGroups.single { it.id == "account" }.title)
        assertNull(SettingsCatalog.mineGroups.single { it.id == "social" }.title)
        assertNull(SettingsCatalog.bottomGroups.single().title)
    }

    @Test
    fun keysAreUniqueAndHideDefaultsAreFalse() {
        assertEquals(SettingsCatalog.all.size, SettingsCatalog.all.map { it.key }.distinct().size)
        assertTrue(SettingsCatalog.all.filter { it.key.startsWith("hide.") }.none { it.defaultValue })
        assertTrue(SettingsCatalog.health.all { it.key.startsWith("hide.health.") })
        assertTrue(SettingsCatalog.sport.all { it.key.startsWith("hide.sport.") })
        assertTrue(SettingsCatalog.device.all { it.key.startsWith("hide.device.") })
        assertTrue(SettingsCatalog.mine.all { it.key.startsWith("hide.mine.") })
        assertTrue(SettingsCatalog.bottom.all { it.key.startsWith("hide.bottom.") })
    }

    @Test
    fun healthQuickEntriesUseOneWholeCardSetting() {
        val quickEntries = SettingsCatalog.healthGroups.single { it.id == "quick-entries" }.settings

        assertEquals(listOf(SettingsKeys.HEALTH_QUICK_ENTRIES), quickEntries.map { it.key })
        assertFalse(SettingsCatalog.defaults.containsKey("hide.health.ai_music"))
        assertFalse(SettingsCatalog.defaults.containsKey("hide.health.management"))
        assertFalse(SettingsCatalog.defaults.containsKey("hide.health.weight_loss"))
        assertFalse(SettingsCatalog.defaults.containsKey("hide.health.smart_training"))
        assertFalse(SettingsCatalog.defaults.containsKey("hide.health.sleep_music"))
    }

    @Test
    fun removedQuickEntryValuesAreNotMigrated() {
        val preferences = InMemoryPreferences(
            mapOf(
                "hide.health.ai_music" to true,
                "hide.health.management" to true,
                "hide.health.weight_loss" to true,
                "hide.health.smart_training" to true,
                "hide.health.sleep_music" to true,
            ),
        )

        val values = SettingsCatalog.read(preferences)

        assertFalse(values.getValue(SettingsKeys.HEALTH_QUICK_ENTRIES))
    }

    @Test
    fun legacyRowsAndTabsAreIgnoredButGeneralValuesArePreserved() {
        val preferences = InMemoryPreferences(
            mapOf(
                SettingsKeys.ENABLED to true,
                SettingsKeys.HIDE_LAUNCHER_ICON to true,
                "row_orders" to true,
                "tab_home" to true,
            ),
        )
        LayoutConfigStore().load(preferences)
        val values = SettingsCatalog.read(preferences)
        assertTrue(values.getValue(SettingsKeys.ENABLED))
        assertTrue(values.getValue(SettingsKeys.HIDE_LAUNCHER_ICON))
        assertFalse(values.getValue(SettingsKeys.MINE_ORDERS))
        assertFalse(values.getValue(SettingsKeys.BOTTOM_HEALTH))
        assertEquals(
            SettingsKeys.CURRENT_SCHEMA_VERSION,
            preferences.getInt(SettingsKeys.SCHEMA_VERSION, 0),
        )
    }

    @Test
    fun masterSwitchAndCategoryHiddenItemAreBothRequired() {
        val hidden = SettingsCatalog.defaults + (SettingsKeys.HEALTH_SEARCH to true)
        assertFalse(SettingsCatalog.hasHidden(SettingsCategory.HEALTH, hidden))
        assertTrue(
            SettingsCatalog.hasHidden(
                SettingsCategory.HEALTH,
                hidden + (SettingsKeys.ENABLED to true),
            ),
        )
    }

    @Test
    fun allHiddenBottomTabsAlwaysRestoreHealth() {
        val allHidden = SettingsCatalog.bottom.associate { it.key to true }
        val normalized = SettingsCatalog.normalizeBottomTabs(allHidden)
        assertFalse(normalized.getValue(SettingsKeys.BOTTOM_HEALTH))
        assertTrue(SettingsCatalog.bottom.drop(1).all { normalized[it.key] == true })

        val healthOnlyHidden = SettingsCatalog.normalizeWrite(
            SettingsKeys.BOTTOM_HEALTH,
            true,
            SettingsCatalog.defaults,
        )
        assertTrue(healthOnlyHidden.getValue(SettingsKeys.BOTTOM_HEALTH))
    }
}
