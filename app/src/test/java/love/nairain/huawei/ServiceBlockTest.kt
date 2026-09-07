package love.nairain.huawei

import android.content.SharedPreferences
import love.nairain.huawei.app.ServiceItem
import love.nairain.huawei.app.visibleServices
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServiceConfigStore
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.feature.ServiceBindingState
import love.nairain.huawei.hook.feature.ServiceHookInstallation
import org.junit.Assert.*
import org.junit.Test

class ServiceBlockTest {
    private val pkg = "com.huawei.health"
    private val first = "$pkg/$pkg.FirstService"
    private val second = "$pkg/vendor.SecondService"

    @Test fun defaultsAndLayoutSwitchAreIndependent() {
        assertEquals(ServiceBlockConfig(), ServiceBlockConfig.read(InMemoryPreferences()))
        val prefs = InMemoryPreferences(mapOf(SettingsKeys.ENABLED to true))
        assertFalse(ServiceBlockConfig.read(prefs).enabled)
        val config = ServiceBlockConfig(true, setOf(first))
        assertTrue(ServiceBlockConfig.write(prefs, config))
        prefs.edit().putBoolean(SettingsKeys.ENABLED, false).apply()
        assertEquals(config, ServiceBlockConfig.read(prefs))
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

    @Test fun corruptTypesAreReportedAndCollectionsAreCopied() {
        listOf(
            mapOf(ServiceBlockConfig.ENABLED to "true"),
            mapOf(ServiceBlockConfig.COMPONENTS to listOf(first)),
            mapOf(ServiceBlockConfig.COMPONENTS to setOf(123)),
        ).forEach { values ->
            assertThrows(IllegalArgumentException::class.java) { ServiceBlockConfig.read(InMemoryPreferences(values)) }
        }
        val source = mutableSetOf(first)
        val config = ServiceBlockConfig.read(InMemoryPreferences(mapOf(ServiceBlockConfig.COMPONENTS to source)))
        source.clear()
        assertEquals(setOf(first), config.components)
    }

    @Test fun failedCommitRestoresPreviousValuesIncludingOptimisticCache() {
        val memory = InMemoryPreferences()
        var commits = 0
        val prefs = failingPreferences(memory) { ++commits != 1 }
        val result = ServiceConfigStore.save(prefs, ServiceBlockConfig(), ServiceBlockConfig(true, setOf(first)))
        assertFalse(result.saved)
        assertTrue(result.restored)
        assertEquals(2, commits)
        assertEquals(ServiceBlockConfig(), ServiceBlockConfig.read(memory))
    }

    @Test fun rollbackFailureIsNotReportedAsSuccessfulSave() {
        val result = ServiceConfigStore.save(failingPreferences(InMemoryPreferences()) { false },
            ServiceBlockConfig(), ServiceBlockConfig(true, setOf(first)))
        assertFalse(result.saved)
        assertFalse(result.restored)
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

    private fun failingPreferences(memory: InMemoryPreferences, decideCommit: () -> Boolean): SharedPreferences =
        object : SharedPreferences by memory {
            override fun edit(): SharedPreferences.Editor {
                val editor = memory.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                        editor.putBoolean(key, value)
                        return this
                    }
                    override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
                        editor.putStringSet(key, values)
                        return this
                    }
                    override fun commit(): Boolean {
                        editor.apply()
                        return decideCommit()
                    }
                }
            }
        }
}
