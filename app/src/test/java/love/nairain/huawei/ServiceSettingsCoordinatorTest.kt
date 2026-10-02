package love.nairain.huawei

import android.content.SharedPreferences
import love.nairain.huawei.app.ServiceBlockUiState
import love.nairain.huawei.app.ServiceCatalog
import love.nairain.huawei.app.ServiceItem
import love.nairain.huawei.app.ServiceSettingsCoordinator
import love.nairain.huawei.app.ServiceSettingsListener
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServicePreset
import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ServiceSettingsCoordinatorTest {
    private val direct = Executor(Runnable::run)
    private val component = "com.huawei.health/com.huawei.health.TestService"
    private val catalog = ServiceCatalog(listOf(ServiceItem(component)), supported = true)

    @Test fun queuedSaveKeepsConfirmedConfigAndRejectsRapidSecondToggle() {
        val worker = ServiceTasks()
        val coordinator = coordinator(worker)
        val preferences = ScriptedPreferences()
        coordinator.bind(Any()) { preferences }
        worker.runAll()
        val components = mutableSetOf(component)
        assertTrue(coordinator.save(ServiceBlockConfig(true, components, debugMode = true)))
        components.clear()
        assertEquals(ServiceBlockConfig(), coordinator.currentState().config)
        assertTrue(coordinator.currentState().saving)
        assertFalse(coordinator.currentState().writable)
        assertFalse(coordinator.save(ServiceBlockConfig(true)))
        coordinator.refresh()
        assertEquals(1, worker.size)
        worker.runAll()
        assertEquals(ServiceBlockConfig(true, setOf(component), debugMode = true), coordinator.currentState().config)
        assertTrue(coordinator.currentState().writable)
        assertEquals(R.string.settings_restart_notice, coordinator.currentState().message)
    }

    @Test fun failedSaveRestoresConfirmedConfigAndMissingKeys() {
        val worker = ServiceTasks()
        val coordinator = coordinator(worker)
        val preferences = ScriptedPreferences(emptyMap(), CommitAction.RETURN_FALSE, CommitAction.RETURN_TRUE)
        coordinator.bind(Any()) { preferences }
        worker.runAll()
        coordinator.save(ServiceBlockConfig(true))
        worker.runAll()
        assertEquals(ServiceBlockConfig(), coordinator.currentState().config)
        assertEquals(emptyMap<String, Any>(), preferences.all)
        assertTrue(coordinator.currentState().writable)
        assertEquals(R.string.settings_status_save_error, coordinator.currentState().message)
    }

    @Test fun uncertainBindingSurvivesRefreshRecreationAndSameIdentityRebind() {
        val worker = ServiceTasks()
        val coordinator = coordinator(worker)
        val identity = Any()
        val preferences = ScriptedPreferences(emptyMap(), CommitAction.RETURN_FALSE, CommitAction.RETURN_FALSE)
        var reads = 0
        coordinator.bind(identity) { reads++; preferences }
        worker.runAll()
        coordinator.save(ServiceBlockConfig(true))
        worker.runAll()
        assertUncertain(coordinator.currentState())
        assertEquals(2, reads)
        coordinator.refresh()
        worker.runAll()
        assertEquals(2, reads)
        val oldStates = mutableListOf<ServiceBlockUiState>()
        val oldListener = ServiceSettingsListener(oldStates::add)
        coordinator.addListener(oldListener)
        coordinator.removeListener(oldListener)
        val newStates = mutableListOf<ServiceBlockUiState>()
        coordinator.addListener(newStates::add)
        assertUncertain(newStates.last())
        val oldSize = oldStates.size
        coordinator.bind(identity) { fail("Same identity must not replace the uncertain source"); preferences }
        coordinator.bind(null)
        coordinator.bind(identity) { reads++; preferences }
        worker.runAll()
        assertUncertain(coordinator.currentState())
        assertEquals(2, reads)
        assertEquals(oldSize, oldStates.size)
        assertFalse(coordinator.save(ServiceBlockConfig(true)))

        var unavailable = true
        coordinator.bind(Any()) {
            if (unavailable) error("configuration unavailable")
            InMemoryPreferences()
        }
        worker.runAll()
        assertFalse(coordinator.currentState().writable)
        assertEquals(R.string.settings_status_config_error, coordinator.currentState().message)
        unavailable = false
        coordinator.refresh()
        worker.runAll()
        assertTrue(coordinator.currentState().writable)
        assertEquals(ServiceBlockConfig(), coordinator.currentState().config)
    }

    @Test fun rebindCancelsQueuedOldSaveAndUsesFreshConfirmedValue() {
        val worker = ServiceTasks()
        val coordinator = coordinator(worker)
        val old = InMemoryPreferences()
        coordinator.bind(Any()) { old }
        worker.runAll()
        assertTrue(coordinator.save(ServiceBlockConfig(true)))
        val next = InMemoryPreferences(mapOf(ServiceBlockConfig.DEBUG_MODE to true))
        coordinator.bind(Any()) { next }
        worker.runAll()
        assertTrue(old.all.isEmpty())
        assertEquals(ServiceBlockConfig(debugMode = true), coordinator.currentState().config)
        assertTrue(coordinator.currentState().writable)
    }

    @Test fun removedPageGetsNoQueuedNotificationsAndNewPageGetsCurrentState() {
        val worker = ServiceTasks()
        val main = ServiceTasks()
        val coordinator = ServiceSettingsCoordinator(worker, main) { catalog }
        val old = mutableListOf<ServiceBlockUiState>()
        val listener = ServiceSettingsListener(old::add)
        coordinator.addListener(listener)
        coordinator.bind(Any()) { InMemoryPreferences() }
        worker.runAll()
        coordinator.removeListener(listener)
        val current = mutableListOf<ServiceBlockUiState>()
        coordinator.addListener(current::add)
        main.runAll()
        assertTrue(old.isEmpty())
        assertTrue(current.isNotEmpty())
        assertTrue(current.all { it.writable })
    }

    @Test fun unsupportedCatalogAllowsRemovalButRejectsNewBlockRules() {
        val worker = ServiceTasks()
        val coordinator = ServiceSettingsCoordinator(worker, direct) { ServiceCatalog() }
        val initial = ServiceBlockConfig(true, setOf(component))
        val preferences = InMemoryPreferences()
        ServiceBlockConfig.write(preferences, initial)
        coordinator.bind(Any()) { preferences }
        worker.runAll()
        assertFalse(coordinator.save(initial.copy(presets = setOf(ServicePreset.DEVICE_ASSIST.id))))
        assertFalse(coordinator.save(initial.copy(debugMode = true)))
        assertFalse(coordinator.save(initial.copy(components = initial.components + "$component.New")))
        assertTrue(coordinator.save(initial.copy(enabled = false, components = emptySet())))
        worker.runAll()
        assertFalse(coordinator.currentState().config.enabled)
        assertTrue(coordinator.currentState().config.components.isEmpty())
    }

    @Test fun corruptReadAndSourceFailureNeverPublishEditableConfig() {
        val worker = ServiceTasks()
        val coordinator = coordinator(worker)
        var failRead = false
        val preferences = InMemoryPreferences()
        coordinator.bind(Any()) { if (failRead) error("source failure") else preferences }
        worker.runAll()
        failRead = true
        coordinator.save(ServiceBlockConfig(true))
        worker.runAll()
        assertFalse(coordinator.currentState().writable)
        assertFalse(coordinator.currentState().config.enabled)
        assertEquals(R.string.settings_status_config_error, coordinator.currentState().message)
        assertTrue(preferences.all.isEmpty())
        coordinator.bind(Any()) { InMemoryPreferences(mapOf(ServiceBlockConfig.ENABLED to "true")) }
        worker.runAll()
        assertFalse(coordinator.currentState().writable)
    }

    @Test fun failedCatalogReadStillAllowsTurningOffExistingConfiguration() {
        val worker = ServiceTasks()
        val coordinator = ServiceSettingsCoordinator(worker, direct) { error("package query failure") }
        coordinator.bind(Any()) { InMemoryPreferences(mapOf(ServiceBlockConfig.ENABLED to true)) }
        worker.runAll()
        assertEquals(R.string.service_block_load_error, coordinator.currentState().catalog.status)
        assertTrue(coordinator.save(ServiceBlockConfig()))
        worker.runAll()
        assertFalse(coordinator.currentState().config.enabled)
    }

    @Test fun catalogAndRemotePreferencesRunOnWorkerThread() {
        val worker = Executors.newSingleThreadExecutor { Thread(it, "service-settings-test") }
        val remoteThread = AtomicReference<String>()
        val catalogThread = AtomicReference<String>()
        val ready = CountDownLatch(1)
        try {
            val coordinator = ServiceSettingsCoordinator(worker, direct) {
                catalogThread.set(Thread.currentThread().name)
                catalog
            }
            coordinator.addListener { if (it.writable) ready.countDown() }
            coordinator.bind(Any()) { remoteThread.set(Thread.currentThread().name); InMemoryPreferences() }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            assertEquals("service-settings-test", remoteThread.get())
            assertEquals("service-settings-test", catalogThread.get())
        } finally {
            worker.shutdownNow()
        }
    }

    @Test fun freshBindingWinsWhenOldSaveAndRollbackFinishLate() {
        rebindDuringCommit(sameIdentity = false)
    }

    @Test fun reconnectingSameIdentityDuringFailedCommitRemainsUncertain() {
        rebindDuringCommit(sameIdentity = true)
    }

    private fun rebindDuringCommit(sameIdentity: Boolean) {
        val worker = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val initialReady = CountDownLatch(1)
        val oldMemory = ScriptedPreferences(emptyMap(), CommitAction.RETURN_FALSE, CommitAction.RETURN_FALSE)
        var commits = 0
        val blocked = object : SharedPreferences by oldMemory {
            override fun edit(): SharedPreferences.Editor {
                val editor = oldMemory.edit()
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
                        val result = editor.commit()
                        if (++commits == 1) {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                        return result
                    }
                }
            }
        }
        try {
            val coordinator = coordinator(worker)
            coordinator.addListener { if (it.writable) initialReady.countDown() }
            val identity = Any()
            coordinator.bind(identity) { blocked }
            assertTrue(initialReady.await(5, TimeUnit.SECONDS))
            assertTrue(coordinator.save(ServiceBlockConfig(true)))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            coordinator.bind(null)
            if (sameIdentity) coordinator.bind(identity) { blocked }
            else coordinator.bind(Any()) { InMemoryPreferences(mapOf(ServiceBlockConfig.DEBUG_MODE to true)) }
            assertFalse(coordinator.currentState().writable)
            assertFalse(coordinator.currentState().config.enabled)
            release.countDown()
            worker.submit {}.get(5, TimeUnit.SECONDS)
            if (sameIdentity) {
                assertUncertain(coordinator.currentState())
                coordinator.refresh()
                worker.submit {}.get(5, TimeUnit.SECONDS)
                assertUncertain(coordinator.currentState())
            } else {
                assertTrue(coordinator.currentState().writable)
                assertEquals(ServiceBlockConfig(debugMode = true), coordinator.currentState().config)
            }
            assertEquals(2, commits)
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    private fun coordinator(worker: Executor) = ServiceSettingsCoordinator(worker, direct) { catalog }

    private fun assertUncertain(state: ServiceBlockUiState) {
        assertFalse(state.writable)
        assertFalse(state.config.enabled)
        assertEquals(R.string.service_block_save_uncertain, state.message)
    }
}

private class ServiceTasks : Executor {
    private val tasks = ArrayDeque<Runnable>()
    val size: Int get() = tasks.size
    override fun execute(command: Runnable) { tasks.addLast(command) }
    fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
}
