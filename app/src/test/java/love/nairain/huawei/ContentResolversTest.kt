package love.nairain.huawei

import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.resolver.BottomTabKeyResolver
import love.nairain.huawei.hook.resolver.DeviceContentKeyResolver
import love.nairain.huawei.hook.resolver.HealthContentKeyResolver
import love.nairain.huawei.hook.resolver.ListFilters
import love.nairain.huawei.hook.resolver.RowKeyResolver
import love.nairain.huawei.hook.resolver.SportContentKeyResolver
import love.nairain.huawei.hook.resolver.SportPageTargets
import love.nairain.huawei.hook.resolver.SportTab
import love.nairain.huawei.hook.resolver.MineMarketingContentResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentResolversTest {
    @Test
    fun mapsVerifiedHealthIdentifiersAndKeepsUnknown() {
        assertEquals(SettingsKeys.HEALTH_ACTIVITY_RINGS, HealthContentKeyResolver.topCard("SCUI_TwoModelCardData"))
        assertEquals(SettingsKeys.HEALTH_QUICK_ENTRIES, HealthContentKeyResolver.topCard("FunctionMenuCardData"))
        assertEquals(SettingsKeys.HEALTH_QUICK_ENTRIES, HealthContentKeyResolver.topCard("HealthQuickEntryCardData"))
        assertNull(HealthContentKeyResolver.topCard("new-server-card"))
    }

    @Test
    fun filtersLegacyAndModernQuickCardsAsOneFeature() {
        val source = listOf("FunctionMenuCardData", "HealthQuickEntryCardData", "new-server-card")

        assertEquals(
            listOf("new-server-card"),
            ListFilters.copyAndFilter(source, HealthContentKeyResolver::topCard,
                mapOf(SettingsKeys.HEALTH_QUICK_ENTRIES to true)),
        )
        assertEquals(
            source,
            ListFilters.copyAndFilter(source, HealthContentKeyResolver::topCard,
                mapOf(SettingsKeys.HEALTH_QUICK_ENTRIES to false)),
        )
    }

    @Test
    fun mapsVerifiedSportMineAndBottomIdentifiers() {
        assertEquals(SettingsKeys.SPORT_BANNER, SportContentKeyResolver.resolve("view_sport_banner_root", null, null))
        assertEquals(SettingsKeys.SPORT_GOLF, SportContentKeyResolver.resolve(null, "GolfClubsEnterProvider", null))
        assertEquals(SettingsKeys.SPORT_COACHES, SportContentKeyResolver.resolve(null, null, "明星教练"))
        assertEquals(SettingsKeys.MINE_ABOUT, RowKeyResolver().resolve("IDS_settings_about"))
        assertEquals(SettingsKeys.MINE_FEEDBACK, RowKeyResolver().resolve("IDS_user_profile_questions_suggestions"))
        assertEquals(SettingsKeys.MINE_COURSES, RowKeyResolver().resolve("2130837547", 0x7f02002b))
        assertEquals(SettingsKeys.MINE_PROFILE, RowKeyResolver().resolve("2130841992", 0x7f021188))
        assertEquals(SettingsKeys.MINE_ABOUT, RowKeyResolver().resolve("2130841936", 0x7f021150))
        assertEquals(SettingsKeys.BOTTOM_MEMBER, BottomTabKeyResolver().resolve("IDS_vip"))
        assertNull(RowKeyResolver().resolve("new_dynamic_row"))
    }

    @Test
    fun sportTabsArePageIdentitiesAndWholeQuickEntrySwitchSuspendsIndividualChoice() {
        assertEquals(SportTab.RECOMMEND, SportPageTargets.tabs["推荐"])
        assertEquals(SportTab.PLAN, SportPageTargets.tabs["计划"])
        assertEquals(SportTab.RUN, SportPageTargets.tabs["户外跑步"])
        assertEquals(SportTab.YOGA, SportPageTargets.tabs["瑜伽"])
        assertEquals(SportTab.FITNESS, SportPageTargets.tabs["健身"])
        val selected = mapOf(SettingsKeys.SPORT_STRETCH to true)
        assertEquals(SettingsKeys.SPORT_STRETCH,
            SportPageTargets.effectiveQuickEntryKey(SettingsKeys.SPORT_STRETCH, selected))
        val whole = selected + (SettingsKeys.SPORT_QUICK_ENTRIES to true)
        assertNull(SportPageTargets.effectiveQuickEntryKey(SettingsKeys.SPORT_STRETCH, whole))
        assertEquals(SettingsKeys.SPORT_LATEST,
            SportPageTargets.effectiveQuickEntryKey(SettingsKeys.SPORT_LATEST, whole))
        assertEquals(listOf(SettingsKeys.SPORT_STRETCH), ListFilters.copyAndFilter(
            listOf(SettingsKeys.SPORT_STRETCH),
            { SportPageTargets.effectiveQuickEntryKey(it, whole) }, whole))
    }

    @Test
    fun everyDeviceSettingHasVerifiedResourceMapping() {
        val legacy = DeviceContentKeyResolver.resourceMappings.values.toSet()
        val newParent = DeviceContentKeyResolver.newParentMappings.values.toSet()
        val arkui = DeviceContentKeyResolver.arkuiDelegateKeys.values.toSet()
        assertTrue(SettingsCatalog.device.map { it.key }.toSet().containsAll(legacy + newParent + arkui))
        assertEquals(SettingsKeys.DEVICE_PRIMARY,
            DeviceContentKeyResolver.arkuiDelegateKeys["rxl"])
        assertEquals(SettingsKeys.DEVICE_SEARCH,
            DeviceContentKeyResolver.newParentMappings["hwappbarpattern_layout_ok_icon"])
        assertEquals(
            SettingsKeys.DEVICE_MENU,
            DeviceContentKeyResolver.resolve("hwappbarpattern_layout_ok_icon"),
        )
        assertNull(DeviceContentKeyResolver.resolve("new_server_device_block"))
    }

    @Test
    fun mineMarketingFilterRemovesOnlyVerifiedPositionIdsWhenEnabled() {
        val source = linkedMapOf<Any, String>(
            MineMarketingContentResolver.NEW_PRODUCT_POSITION_ID to "new product",
            MineMarketingContentResolver.NEW_USER_BENEFIT_POSITION_ID to "new user benefit",
            7777 to "unknown",
        )

        assertEquals(source, MineMarketingContentResolver.filter(source, enabled = false))
        assertEquals(
            mapOf(7777 to "unknown"),
            MineMarketingContentResolver.filter(source, enabled = true),
        )
    }
}
