package love.nairain.huawei

import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.sourceLayoutConfig
import org.junit.Assert.*
import org.junit.Test

class LayoutSourceConfigTest {
    private val config = mapOf(SettingsKeys.ENABLED to true, SettingsKeys.DEVICE_LIST to true,
        SettingsKeys.DEVICE_PRIMARY to true, SettingsKeys.DEVICE_TIPS to false)

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
