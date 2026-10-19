package love.nairain.huawei

import love.nairain.huawei.app.ServiceItem
import love.nairain.huawei.app.visibleServices
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServicePreset
import love.nairain.huawei.config.ServiceSection
import love.nairain.huawei.hook.feature.ServiceBindingState
import love.nairain.huawei.hook.feature.ServiceHookInstallation
import org.junit.Assert.*
import org.junit.Test

class ServiceBlockTest {
    private val pkg = "com.huawei.health"
    private val first = "$pkg/$pkg.FirstService"
    private val second = "$pkg/vendor.SecondService"

    @Test fun businessGroupsMergeAndKeepLegacyRulesNarrow() {
        assertEquals(ServiceSection.LOW_COUPLING, ServicePreset.DEVICE_ASSIST.section)
        assertEquals(3, ServicePreset.DEVICE_ASSIST.components.size)
        assertEquals(setOf("com.huawei.health/com.huawei.hwdevice.mainprocess.service.SyncMusicService"),
            ServicePreset.componentsFor("music_sync"))
        val legacy = setOf("app_update")
        assertTrue(ServicePreset.hasLegacySelection(legacy, ServicePreset.DEVICE_UPDATE))
        assertEquals(setOf("legacy.app_update.app"), ServicePreset.changeSelection(legacy, ServicePreset.DEVICE_UPDATE, false))
    }

    @Test fun presetsRespectDebugModeAndDeclaredServices() {
        val update = ServicePreset.APP_UPDATE.components.first()
        val config = ServiceBlockConfig(true, setOf(first), setOf(ServicePreset.APP_UPDATE.id), false)
        assertTrue(update in config.effectiveComponents(setOf(update)))
        assertFalse(first in config.effectiveComponents(setOf(first)))
        val debug = config.copy(debugMode = true)
        assertTrue(first in debug.effectiveComponents(setOf(first)))
        assertTrue(update in debug.effectiveComponents(setOf(update)))
    }


    @Test fun componentNormalizationAndExactMatching() {
        assertEquals(first, ServiceBlockConfig.normalize("$pkg/.FirstService"))
        assertEquals(first, ServiceBlockConfig.componentName(pkg, "FirstService"))
        assertNull(ServiceBlockConfig.normalize("$pkg/.FirstService/extra"))
        assertNull(ServiceBlockConfig.componentName("other.app", "FirstService"))
        assertNull(ServiceBlockConfig.componentName(pkg, "foo.*"))
        assertEquals(second, ServiceBlockConfig.componentName(pkg, "vendor.SecondService"))
        val config = ServiceBlockConfig(true, setOf(first, second))
        assertTrue(config.blocks(pkg, ".FirstService", setOf(first)))
        assertFalse(config.blocks(pkg, ".FirstServiceExtra", setOf(first)))
        assertFalse(config.blocks(pkg, "vendor.SecondService", setOf(first)))
        assertFalse(config.blocks(null, null, setOf(first)))
        assertFalse(config.blocks("other.app", "$pkg.FirstService", setOf(first)))
        assertFalse(config.copy(enabled = false).blocks(pkg, ".FirstService", setOf(first)))
    }








    @Test fun searchIncludesProcessAndMissingRulesCanBeRemoved() {
        val services = listOf(ServiceItem(first, "$pkg:sync"))
        val selected = setOf(second)
        assertEquals(listOf(first), visibleServices(services, selected, "SYNC", false).map { it.component })
        val missing = visibleServices(services, selected, "", true).single()
        assertEquals(second, missing.component)
        assertTrue(missing.missing)
        assertTrue(visibleServices(services, emptySet(), "", true).isEmpty())
    }

    @Test fun bindingStateIsWeakIdentityScopedAndPreservesRealAttempts() {
        val state = ServiceBindingState()
        val context = Any()
        val otherContext = Any()
        val connection = Any()
        state.record(context, connection, true)
        state.record(context, connection, true)
        assertFalse(state.consumeBlockedUnbind(otherContext, connection))
        assertTrue(state.consumeBlockedUnbind(context, connection))
        assertFalse(state.consumeBlockedUnbind(context, connection))
        state.record(context, connection, false)
        state.record(context, connection, true)
        assertFalse(state.consumeBlockedUnbind(context, connection))
        state.record(context, connection, true)
        state.record(context, connection, false)
        assertFalse(state.consumeBlockedUnbind(context, connection))
    }

    @Test fun installationIsInactiveUntilCompleteAndRollsBackInReverseOrder() {
        val transaction = ServiceHookInstallation()
        val removed = mutableListOf<Int>()
        val errors = mutableListOf<Throwable>()
        transaction.install(errors::add) {
            assertFalse(active)
            register { removed += 1 }
            register { removed += 2; error("rollback failure") }
            error("installation failure")
        }
        assertFalse(transaction.active)
        assertEquals(listOf(2, 1), removed)
        assertEquals(2, errors.size)
        val success = ServiceHookInstallation()
        success.install({ throw AssertionError(it) }) { register { fail("must not unhook") } }
        assertTrue(success.active)
    }

}
