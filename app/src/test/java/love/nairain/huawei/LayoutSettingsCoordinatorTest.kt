package love.nairain.huawei

import love.nairain.huawei.app.LayoutPreferencesSource
import love.nairain.huawei.app.LayoutSettingsCoordinator
import love.nairain.huawei.app.SettingsNoticeKind
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LayoutSettingsCoordinatorTest {
    private val directExecutor = Executor(Runnable::run)

    @Test
    fun savePublishesOnlyConfirmedValueAndRejectsRapidSecondToggle() {
        val worker = QueuedExecutor()
        val coordinator = coordinator(worker)
        val preferences = ScriptedPreferences(completePreferences())
        coordinator.bind { preferences }
        worker.runAll()

        assertTrue(coordinator.save(SettingsKeys.HEALTH_SEARCH, true))
        assertFalse(coordinator.currentState().valueOf(SettingsKeys.HEALTH_SEARCH))
        assertTrue(coordinator.currentState().isSaving)
        assertFalse(coordinator.save(SettingsKeys.HEALTH_SEARCH, true))

        worker.runAll()

        assertTrue(coordinator.currentState().valueOf(SettingsKeys.HEALTH_SEARCH))
        assertEquals(SettingsNoticeKind.RESTART_REQUIRED, coordinator.currentState().notice?.kind)
    }

    @Test
    fun failedSaveKeepsPreviousConfirmedValue() {
        val worker = QueuedExecutor()
        val coordinator = coordinator(worker)
        val preferences = ScriptedPreferences(
            completePreferences(),
            CommitAction.RETURN_FALSE,
            CommitAction.RETURN_TRUE,
        )
        coordinator.bind { preferences }
        worker.runAll()

        coordinator.save(SettingsKeys.HEALTH_SEARCH, true)
        worker.runAll()

        assertFalse(coordinator.currentState().valueOf(SettingsKeys.HEALTH_SEARCH))
        assertTrue(coordinator.currentState().writable)
        assertEquals(SettingsNoticeKind.SAVE_FAILED, coordinator.currentState().notice?.kind)
    }

    @Test
    fun refreshQueuedDuringSaveReadsRealSerializedResult() {
        val worker = QueuedExecutor()
        val coordinator = coordinator(worker)
        val preferences = ScriptedPreferences(completePreferences())
        coordinator.bind { preferences }
        worker.runAll()

        coordinator.save(SettingsKeys.HEALTH_SEARCH, true)
        coordinator.refresh()
        assertTrue(coordinator.currentState().isSaving)
        assertTrue(coordinator.currentState().isLoading)
        worker.runNext()
        assertFalse(coordinator.currentState().valueOf(SettingsKeys.HEALTH_SEARCH))

        worker.runNext()

        assertTrue(coordinator.currentState().valueOf(SettingsKeys.HEALTH_SEARCH))
        assertTrue(coordinator.currentState().writable)
    }

    @Test
    fun disconnectKeepsConfirmedValuesAndReconnectLoadsNewBinding() {
        val worker = QueuedExecutor()
        val coordinator = coordinator(worker)
        val first = ScriptedPreferences(
            completePreferences(SettingsCatalog.defaults + (SettingsKeys.ENABLED to true)),
        )
        coordinator.bind { first }
        worker.runAll()
        assertTrue(coordinator.currentState().valueOf(SettingsKeys.ENABLED))

        coordinator.bind(null)
        assertTrue(coordinator.currentState().valueOf(SettingsKeys.ENABLED))
        assertFalse(coordinator.currentState().writable)

        val second = ScriptedPreferences(completePreferences())
        coordinator.bind { second }
        worker.runAll()
        assertFalse(coordinator.currentState().valueOf(SettingsKeys.ENABLED))
        assertTrue(coordinator.currentState().writable)
    }

    @Test
    fun obsoleteBindingReadCannotReplaceNewState() {
        val worker = QueuedExecutor()
        val coordinator = coordinator(worker)
        var oldCalls = 0
        val old = ScriptedPreferences(
            completePreferences(SettingsCatalog.defaults + (SettingsKeys.ENABLED to true)),
        )
        val current = ScriptedPreferences(completePreferences())
        coordinator.bind { oldCalls += 1; old }
        coordinator.bind { current }

        worker.runAll()

        assertEquals(0, oldCalls)
        assertFalse(coordinator.currentState().valueOf(SettingsKeys.ENABLED))
    }

    @Test
    fun defaultRollbackFailureBlocksSameBindingUntilRebind() {
        val worker = QueuedExecutor()
        val coordinator = coordinator(worker)
        var calls = 0
        val uncertain = ScriptedPreferences(
            emptyMap(),
            CommitAction.RETURN_FALSE,
            CommitAction.RETURN_FALSE,
        )
        coordinator.bind { calls += 1; uncertain }
        worker.runAll()

        assertFalse(coordinator.currentState().writable)
        assertEquals(SettingsNoticeKind.STATE_UNCERTAIN, coordinator.currentState().notice?.kind)
        coordinator.refresh()
        worker.runAll()
        assertEquals(1, calls)

        coordinator.bind { ScriptedPreferences(completePreferences()) }
        worker.runAll()
        assertTrue(coordinator.currentState().writable)
    }

    @Test
    fun saveRollbackFailureBlocksRefreshUntilNewBinding() {
        val worker = QueuedExecutor()
        val coordinator = coordinator(worker)
        var calls = 0
        val uncertain = ScriptedPreferences(
            completePreferences(),
            CommitAction.RETURN_FALSE,
            CommitAction.RETURN_FALSE,
        )
        coordinator.bind { calls += 1; uncertain }
        worker.runAll()

        coordinator.save(SettingsKeys.HEALTH_SEARCH, true)
        worker.runAll()

        assertFalse(coordinator.currentState().valueOf(SettingsKeys.HEALTH_SEARCH))
        assertFalse(coordinator.currentState().writable)
        assertEquals(SettingsNoticeKind.STATE_UNCERTAIN, coordinator.currentState().notice?.kind)
        coordinator.refresh()
        worker.runAll()
        assertEquals(2, calls)
    }

    @Test
    fun olderRefreshRevisionIsSkipped() {
        val worker = QueuedExecutor()
        val coordinator = coordinator(worker)
        var calls = 0
        val source: LayoutPreferencesSource = {
            calls += 1
            ScriptedPreferences(completePreferences())
        }
        coordinator.bind(source)
        coordinator.refresh()

        worker.runAll()

        assertEquals(1, calls)
        assertTrue(coordinator.currentState().isConfigAvailable)
    }

    @Test
    fun recoveredDefaultFailureRemainsEditableWithNotice() {
        val worker = QueuedExecutor()
        val coordinator = coordinator(worker)
        val preferences = ScriptedPreferences(
            emptyMap(),
            CommitAction.RETURN_FALSE,
            CommitAction.RETURN_TRUE,
        )
        coordinator.bind { preferences }

        worker.runAll()

        assertTrue(coordinator.currentState().writable)
        assertEquals(
            SettingsNoticeKind.DEFAULTS_NOT_PERSISTED,
            coordinator.currentState().notice?.kind,
        )
    }

    @Test
    fun allSubscribersReceiveTheSameConfirmedState() {
        val worker = QueuedExecutor()
        val coordinator = coordinator(worker)
        val first = mutableListOf<Boolean>()
        val second = mutableListOf<Boolean>()
        coordinator.addListener { first += it.valueOf(SettingsKeys.ENABLED) }
        coordinator.addListener { second += it.valueOf(SettingsKeys.ENABLED) }
        coordinator.bind {
            ScriptedPreferences(
                completePreferences(SettingsCatalog.defaults + (SettingsKeys.ENABLED to true)),
            )
        }

        worker.runAll()

        assertEquals(first.last(), second.last())
        assertTrue(first.last())
    }

    @Test
    fun preferencesProviderRunsOffCallingThread() {
        val caller = Thread.currentThread().name
        val providerThread = AtomicReference<String>()
        val ready = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "settings-worker-test") }
        try {
            val coordinator = LayoutSettingsCoordinator(worker, directExecutor)
            coordinator.addListener { state -> if (state.isConfigAvailable) ready.countDown() }
            coordinator.bind {
                providerThread.set(Thread.currentThread().name)
                ScriptedPreferences(completePreferences())
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS))
            assertNotEquals(caller, providerThread.get())
            assertEquals("settings-worker-test", providerThread.get())
        } finally {
            worker.shutdownNow()
        }
    }

    private fun coordinator(worker: Executor) = LayoutSettingsCoordinator(worker, directExecutor)
}

private class QueuedExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        tasks.addLast(command)
    }

    fun runNext() {
        tasks.removeFirst().run()
    }

    fun runAll() {
        while (tasks.isNotEmpty()) runNext()
    }
}
