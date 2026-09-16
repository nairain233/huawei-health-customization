package love.nairain.huawei

import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.resolver.BottomTabKeyResolver
import love.nairain.huawei.hook.resolver.DeviceContentKeyResolver
import love.nairain.huawei.hook.resolver.HealthContentKeyResolver
import love.nairain.huawei.hook.resolver.RowKeyResolver
import love.nairain.huawei.hook.resolver.SportContentKeyResolver
import love.nairain.huawei.hook.resolver.MineMarketingContentResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentResolversTest {
    @Test
    fun mapsVerifiedHealthIdentifiersAndKeepsUnknown() {
        assertEquals(SettingsKeys.HEALTH_ACTIVITY_RINGS, HealthContentKeyResolver.topCard("SCUI_TwoModelCardData"))
        assertEquals(SettingsKeys.HEALTH_QUICK_ENTRIES, HealthContentKeyResolver.topCard("FunctionMenuCardData"))
        assertNull(HealthContentKeyResolver.topCard("new-server-card"))
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
    fun everyDeviceSettingHasVerifiedResourceMapping() {
        val mappedKeys = DeviceContentKeyResolver.resourceMappings.values.toSet()
        assertEquals(SettingsCatalog.device.map { it.key }.toSet(), mappedKeys)
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
