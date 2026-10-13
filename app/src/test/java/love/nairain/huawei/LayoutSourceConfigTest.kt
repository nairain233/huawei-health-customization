package love.nairain.huawei

import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.sourceLayoutConfig
import org.junit.Assert.*
import org.junit.Test

class LayoutSourceConfigTest {
    private val config = mapOf(SettingsKeys.ENABLED to true, SettingsKeys.DEVICE_LIST to true,
        SettingsKeys.DEVICE_PRIMARY to true, SettingsKeys.DEVICE_TIPS to false)

    @Test fun badgeSourcesNeverBorrowTabOrOtherMineGroupCapabilities() {
        val values = mapOf(SettingsKeys.ENABLED to true, SettingsKeys.BOTTOM_BADGES to true,
            SettingsKeys.BOTTOM_MINE to true, SettingsKeys.MINE_BADGES to true)
        val capabilities = mapOf("bottom.tabs" to setOf(SettingsKeys.BOTTOM_MINE),
            "bottom.badges" to setOf(SettingsKeys.BOTTOM_BADGES), "mine.badges.rows" to setOf(SettingsKeys.MINE_BADGES))
        val tabs = sourceLayoutConfig(values, "bottom.tabs", capabilities)
        assertTrue(tabs.getValue(SettingsKeys.BOTTOM_MINE))
        assertFalse(tabs.getValue(SettingsKeys.BOTTOM_BADGES))
        val badges = sourceLayoutConfig(values, "bottom.badges", capabilities)
        assertTrue(badges.getValue(SettingsKeys.BOTTOM_BADGES))
        assertFalse(badges.getValue(SettingsKeys.BOTTOM_MINE))
        assertFalse(sourceLayoutConfig(values, "mine.badges.header", capabilities).getValue(SettingsKeys.MINE_BADGES))
        assertTrue(sourceLayoutConfig(values, "mine.badges.rows", capabilities).getValue(SettingsKeys.MINE_BADGES))
    }

    @Test fun oldPageSuccessDoesNotEnableFailedSharedKeyInArkuiGroup() {
        val capabilities = mapOf("device.fragment.0" to setOf(SettingsKeys.DEVICE_LIST),
            "device.new.arkui" to setOf(SettingsKeys.DEVICE_PRIMARY))
        val arkui = sourceLayoutConfig(config, "device.new.arkui", capabilities)
        assertTrue(arkui.getValue(SettingsKeys.ENABLED))
        assertTrue(arkui.getValue(SettingsKeys.DEVICE_PRIMARY))
        assertFalse(arkui.getValue(SettingsKeys.DEVICE_LIST))
        assertTrue(sourceLayoutConfig(config, "device.fragment.0", capabilities).getValue(SettingsKeys.DEVICE_LIST))
    }

    @Test fun missingGroupDoesNotBorrowOtherCapabilitiesOrChangeStoredConfiguration() {
        val scoped = sourceLayoutConfig(config, "device.new.delegates", emptyMap())
        assertFalse(scoped.getValue(SettingsKeys.DEVICE_PRIMARY))
        assertTrue(config.getValue(SettingsKeys.DEVICE_PRIMARY))
        assertTrue(config.getValue(SettingsKeys.DEVICE_LIST))
    }

    @Test fun verifiedButDisabledKeysRemainDisabledAndLegacyContextKeepsValues() {
        val scoped = sourceLayoutConfig(config, "device.new.arkui", mapOf("device.new.arkui" to setOf(SettingsKeys.DEVICE_TIPS)))
        assertFalse(scoped.getValue(SettingsKeys.DEVICE_TIPS))
        assertEquals(config, sourceLayoutConfig(config, "device.new.arkui", null))
    }
}
