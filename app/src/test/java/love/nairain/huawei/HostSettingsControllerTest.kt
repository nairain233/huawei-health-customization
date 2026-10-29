package love.nairain.huawei

import love.nairain.huawei.app.ServiceCatalog
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.settings.HostConfiguration
import love.nairain.huawei.settings.HostConfigStorage
import love.nairain.huawei.settings.HostConfigStore
import love.nairain.huawei.settings.HostRestarter
import love.nairain.huawei.settings.HostSettingsController
import love.nairain.huawei.settings.HostSettingsNotice
import love.nairain.huawei.settings.HostSettingsState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Delayed
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** 真实协调器配合手动推进的串行队列，故障与重入不依赖休眠或设备时序。 */
class HostSettingsControllerTest {
    private class Pending(command: Runnable) : FutureTask<Unit>(command, Unit), ScheduledFuture<Unit> {
        override fun getDelay(unit: TimeUnit): Long = 0
        override fun compareTo(other: Delayed): Int = 0
    }

    private class Worker : ScheduledThreadPoolExecutor(1) {
        val tasks = ArrayDeque<Runnable>()
        val delays = mutableListOf<Long>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
            delays += unit.toMillis(delay)
            return Pending(command).also { tasks.addLast(it) }
        }
        fun next() { tasks.removeFirst().run() }
    }

    private class Memory(var raw: String?) : HostConfigStorage {
        var reads = 0
        var writes = 0
        var failure: (() -> Exception?) = { null }
        override fun read(): String? {
            reads++
            failure()?.let { throw it }
            return raw
        }
        override fun write(raw: String) { writes++; this.raw = raw }
    }

    private class Fixture(raw: String? = original.encode()) {
        val disk = Memory(raw)
        val worker = Worker()
        val main = ArrayDeque<() -> Unit>()
        val diagnostics = mutableListOf<String>()
        val traces = mutableListOf<String>()
        val snapshots = mutableListOf<HostConfiguration>()
        var storeCreations = 0
        val client = HostSettingsController(
            { storeCreations++; HostConfigStore(disk) }, worker, { main.addLast(it) }, { ServiceCatalog() }, diagnostics::add, traces::add,
        )
        var state = HostSettingsState()
        val subscription = client.start { state = it }
        fun initialize() { client.initialize(snapshots::add) }
        fun deliver() { worker.next(); main.removeFirst().invoke() }
        fun refresh() { client.refresh(); deliver() }
    }

    @Test fun transientReadFailureRetriesOffMainAndDispatchesOnlyConfirmedSnapshot() {
        val f = Fixture()
        f.disk.failure = { if (f.disk.reads == 1) IOException("private content") else null }
        f.initialize()
        assertEquals(0, f.storeCreations)
        assertFalse(f.client.canRestart)
        f.worker.next()
        assertEquals(1, f.storeCreations)
        assertTrue(f.main.isEmpty())
        assertTrue(f.snapshots.isEmpty())
        assertFalse(f.client.writable)
        f.client.refresh() // 自动重试期间不允许插入另一轮加载。
        assertEquals(1, f.worker.tasks.size)
        assertEquals(listOf(250L), f.worker.delays)
        f.deliver()
        assertEquals(listOf(original), f.snapshots)
        assertEquals(original, f.state.configuration)
        assertEquals(HostSettingsNotice.READY, f.state.notice)
        assertTrue(f.client.canRestart)
        assertEquals(0, f.disk.writes)
        assertFalse(f.diagnostics.joinToString().contains("private content"))
        assertEquals(listOf("Local configuration startup retry scheduled: 1", "Local configuration startup loaded",
            "Local startup snapshot dispatch started", "Local startup snapshot dispatched"), f.traces)
    }

    @Test fun secondTransientFailureUsesLastRetryAndRepeatedInitializeCannotReplaceCallback() {
        val f = Fixture()
        f.disk.failure = { if (f.disk.reads <= 2) IOException("private") else null }
        f.initialize()
        f.client.initialize { fail("初始化回调不能被替换") }
        f.worker.next()
        f.worker.next()
        assertEquals(listOf(250L, 1000L), f.worker.delays)
        assertTrue(f.snapshots.isEmpty())
        f.deliver()
        assertEquals(3, f.disk.reads)
        assertEquals(listOf(original), f.snapshots)
        assertEquals(0, f.disk.writes)
    }

    @Test fun exhaustedRetriesStayUnavailableAndSuccessfulRefreshDispatchesExactlyOnce() {
        val f = Fixture()
        f.disk.failure = { IOException("private") }
        f.initialize()
        f.worker.next()
        f.worker.next()
        f.deliver()
        assertEquals(3, f.disk.reads)
        assertEquals(listOf(250L, 1000L), f.worker.delays)
        assertTrue(f.worker.tasks.isEmpty())
        assertTrue(f.snapshots.isEmpty())
        assertEquals(HostSettingsNotice.UNAVAILABLE, f.state.notice)
        assertFalse(f.client.canRestart)
        assertFalse(f.client.writable)
        f.client.saveLayout(SettingsKeys.ENABLED, false)
        assertTrue(f.worker.tasks.isEmpty())
        assertEquals(0, f.disk.writes)

        f.refresh() // 普通刷新失败不会启动新的自动重试循环。
        assertEquals(4, f.disk.reads)
        assertTrue(f.worker.tasks.isEmpty())
        assertEquals(HostSettingsNotice.UNAVAILABLE, f.state.notice)
        f.disk.failure = { null }
        f.refresh()
        assertEquals(listOf(original), f.snapshots)
        assertTrue(f.client.canRestart)
        f.client.initialize { fail("不应重新初始化") }
        f.refresh()
        assertEquals(listOf(original), f.snapshots)
        assertEquals(original.encode(), f.disk.raw)
        assertEquals(0, f.disk.writes)
    }

    @Test fun invalidOrFutureSchemaIsNeverRetriedOrOverwrittenAndCanRecoverByRefresh() {
        val future = JSONObject(original.encode()).put(SettingsKeys.SCHEMA_VERSION, 999).toString()
        listOf("{broken", future).forEach { raw ->
            val f = Fixture(raw)
            f.initialize()
            f.deliver()
            assertEquals(1, f.disk.reads)
            assertTrue(f.worker.delays.isEmpty())
            assertEquals(raw, f.disk.raw)
            assertEquals(0, f.disk.writes)
            assertTrue(f.snapshots.isEmpty())
            assertFalse(f.client.canRestart)
            f.disk.raw = original.encode()
            f.refresh()
            assertEquals(listOf(original), f.snapshots)
            assertTrue(f.client.canRestart)
        }
    }

    @Test fun nonIoReadFailureStaysUnavailableWithoutRetryOrDefaultWrite() {
        val f = Fixture()
        f.disk.failure = { SecurityException("private") }
        f.initialize()
        f.deliver()
        assertEquals(1, f.disk.reads)
        assertTrue(f.worker.delays.isEmpty())
        assertTrue(f.snapshots.isEmpty())
        assertEquals(HostSettingsNotice.UNAVAILABLE, f.state.notice)
        assertFalse(f.client.canRestart)
        assertEquals(0, f.disk.writes)
    }

    @Test fun newInstallPersistsAndConfirmsSafeDefaultsBeforeDispatch() {
        val f = Fixture(null)
        f.initialize()
        f.deliver()
        assertEquals(listOf(HostConfiguration()), f.snapshots)
        assertEquals(1, f.disk.writes)
        assertEquals(HostConfiguration(), HostConfiguration.decode(requireNotNull(f.disk.raw)))
        assertTrue(f.client.canRestart)
    }

    @Test fun startupRecoveryContinuesWithNoSettingsPageAndLaterPageGetsConfirmedValues() {
        val f = Fixture()
        f.subscription.close()
        f.disk.failure = { if (f.disk.reads == 1) IOException("private") else null }
        f.initialize()
        f.worker.next()
        f.deliver()
        assertEquals(listOf(original), f.snapshots)
        assertFalse(f.client.canRestart)
        f.client.start { f.state = it }
        assertEquals(original, f.state.configuration)
        assertTrue(f.client.canRestart)
        assertEquals(0, f.disk.writes)
    }

    @Test fun snapshotDispatchPrecedesReadyNotificationAndBlocksReentrantSaveAndRestart() {
        val f = Fixture()
        val events = mutableListOf<String>()
        f.client.start {
            if (it.notice == HostSettingsNotice.READY) {
                assertEquals(listOf("dispatch"), events)
                assertTrue(f.client.canRestart)
                events += "ready"
            }
        }
        f.client.initialize {
            events += "dispatch"
            assertEquals(original, it)
            assertFalse(f.client.canRestart)
            assertFalse(f.client.writable)
            f.client.refresh()
            f.client.saveLayout(SettingsKeys.ENABLED, false)
        }
        f.deliver()
        assertEquals(listOf("dispatch", "ready"), events)
        assertTrue(f.diagnostics.isEmpty())
        assertTrue(f.worker.tasks.isEmpty())
        assertEquals(0, f.disk.writes)
    }

    @Test fun callbackFailureCannotReinstallPartiallyInstalledHooksOnRefresh() {
        val f = Fixture()
        var calls = 0
        f.client.initialize {
            calls++
            f.client.initialize { fail("重入不能替换回调") }
            throw IllegalStateException("private hook details")
        }
        f.deliver()
        f.refresh()
        assertEquals(1, calls)
        assertTrue(f.client.canRestart) // 仍可由用户重新启动整个进程。
        assertTrue(f.diagnostics.any { it == "Local startup snapshot dispatch failed: IllegalStateException" })
        assertFalse(f.diagnostics.joinToString().contains("private hook details"))
    }

    @Test fun refreshFailureAfterStartupDoesNotChangeExistingRuntimeSnapshot() {
        val f = Fixture()
        f.initialize()
        f.deliver()
        f.disk.failure = { IOException("private") }
        f.refresh()
        assertFalse(f.client.canRestart)
        assertEquals(original, f.state.configuration)
        f.disk.failure = { null }
        f.refresh()
        assertEquals(listOf(original), f.snapshots)
        assertTrue(f.client.canRestart)
        assertTrue(f.worker.delays.isEmpty())
    }

    @Test fun savingBlocksRestartUntilConfirmedAndNextProcessUsesSavedSnapshot() {
        val f = Fixture()
        f.initialize()
        f.deliver()
        f.client.saveLayout(SettingsKeys.HEALTH_SEARCH, false)
        assertEquals(HostSettingsNotice.SAVING, f.state.notice)
        assertTrue(f.state.configuration.layout.getValue(SettingsKeys.HEALTH_SEARCH))
        assertFalse(f.client.canRestart)
        f.worker.next()
        assertFalse(f.client.canRestart) // 落盘完成但主线程尚未确认。
        f.main.removeFirst().invoke()
        assertEquals(HostSettingsNotice.SAVED, f.state.notice)
        assertTrue(f.client.canRestart)
        val saved = original.copy(layout = original.layout + (SettingsKeys.HEALTH_SEARCH to false))
        assertEquals(saved, HostConfiguration.decode(requireNotNull(f.disk.raw)))
        f.refresh()
        assertEquals(saved, f.state.configuration)
        assertEquals(listOf(original), f.snapshots)

        val events = mutableListOf<String>()
        val restarter = HostRestarter("com.huawei.health", { "com.huawei.health" }, { f.client.canRestart },
            { events += "launch"; true }, { events += "terminate" }, f.diagnostics::add)
        assertEquals(HostRestarter.Result.REQUESTED, restarter.restart {})
        assertEquals(listOf("launch", "terminate"), events)
        val next = Fixture(f.disk.raw)
        next.initialize()
        next.deliver()
        assertEquals(listOf(saved), next.snapshots)
        assertEquals(0, next.disk.writes)
    }

    companion object {
        private val original = HostConfiguration().let {
            it.copy(layout = it.layout + (SettingsKeys.ENABLED to true) + (SettingsKeys.HEALTH_SEARCH to true))
        }
    }
}
