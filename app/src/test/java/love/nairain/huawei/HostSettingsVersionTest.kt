package love.nairain.huawei

import love.nairain.huawei.hook.resolver.HostSettingsTargets
import org.junit.Assert.*
import org.junit.Test

class HostSettingsVersionTest {
    @Test fun settingsRowAcceptsOnlyVerifiedVersionPairs() {
        assertTrue(HostSettingsTargets.accepts("17.0.8.300", 1700008300L))
        assertTrue(HostSettingsTargets.accepts("17.0.8.320", 1700008320L))
        assertFalse(HostSettingsTargets.accepts("17.0.8.320", 1700008300L))
        assertFalse(HostSettingsTargets.accepts("17.0.8.300", 1700008320L))
        assertFalse(HostSettingsTargets.accepts("17.0.8.321", 1700008320L))
        assertFalse(HostSettingsTargets.accepts(null, 1700008320L))
    }
}
