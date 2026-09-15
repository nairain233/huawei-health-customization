package love.nairain.huawei

import android.content.SharedPreferences
import love.nairain.huawei.app.ScanStatusController
import love.nairain.huawei.app.ScanUiState
import love.nairain.huawei.scan.ScanProtocol
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*

class ScanStatusControllerTest {
    private class Main : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
    private class Worker : AbstractExecutorService(), ScheduledExecutorService {
        val tasks = ArrayDeque<Runnable>()
        var tick: Runnable? = null
        var closed = false
        var reject = false
        override fun execute(command: Runnable) {
            if (closed || reject) throw RejectedExecutionException()
            tasks.add(command)
        }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
        override fun shutdown() { closed = true }
        override fun shutdownNow(): MutableList<Runnable> {
            closed = true
            return tasks.toMutableList().also { tasks.clear() }
        }
        override fun isShutdown() = closed
        override fun isTerminated() = closed
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = closed
        override fun scheduleWithFixedDelay(command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
            if (reject) throw RejectedExecutionException()
            assertEquals(2L, delay)
            assertEquals(TimeUnit.SECONDS, unit)
            tick = command
            return object : ScheduledFuture<Unit> {
                override fun getDelay(unit: TimeUnit) = 0L
                override fun compareTo(other: Delayed) = 0
                override fun cancel(mayInterruptIfRunning: Boolean) = true
                override fun isCancelled() = false
                override fun isDone() = false
                override fun get() = Unit
                override fun get(timeout: Long, unit: TimeUnit) = Unit
            }
        }
        override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> = error("unused")
        override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> = error("unused")
        override fun scheduleAtFixedRate(command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*> = error("unused")
    }
    private class Fixture {
        val main = Main()
        val workers = mutableListOf<Worker>()
        val states = mutableListOf<ScanUiState>()
        val identity = Any()
        var prefs: SharedPreferences = InMemoryPreferences()
        var reportRead: () -> ScanUiState = { ScanUiState() }
        var rejectFactory = false
        val controller = ScanStatusController({ reportRead() }, main, {
            Worker().also { it.reject = rejectFactory; workers.add(it) }
        }, states::add)
        val state get() = states.last()
        val worker get() = workers.last()
        fun bind(id: Any? = identity) { controller.bindSource(id) { prefs }; main.drain() }
        fun start() { controller.start(); main.drain() }
        fun pump() { worker.drain(); main.drain() }
        fun ready() { bind(); start(); pump(); assertTrue(state.canRequest) }
        fun stop() { controller.stop(); main.drain() }
        fun request() { controller.request(); main.drain() }
        fun tick() { worker.tick!!.run(); main.drain(); pump() }
    }

    @Test fun delayedMainResultCannotSurviveStopAndRestart() {
        val f = Fixture()
        f.bind(); f.start(); f.worker.drain()
        // 将停止和启动放到旧结果前，模拟已经排队的旧回调。
        val old = f.main.tasks.removeFirst()
        f.stop(); f.start()
        val count = f.states.size
        old.run()
        assertEquals(count, f.states.size)
        assertFalse(f.state.canRequest)
        f.pump()
        assertTrue(f.state.canRequest)
    }

    @Test fun stopDuringReadDropsResultAndDoesNotReadPreferences() {
        val f = Fixture()
        var reads = 0
        f.controller.bindSource(f.identity) { reads++; f.prefs }; f.main.drain()
        f.reportRead = { f.stop(); ScanUiState(expired = true) }
        f.start()
        val count = f.states.size
        f.pump()
        assertEquals(0, reads)
        assertEquals(count, f.states.size)
    }

    @Test fun serviceSwitchRejectsOldResultAndRefreshesImmediately() {
        val f = Fixture()
        f.ready()
        f.reportRead = { ScanUiState(expired = true) }
        f.worker.tick!!.run(); f.main.drain(); f.worker.drain()
        val old = f.main.tasks.removeFirst()
        f.bind(Any())
        assertFalse(f.state.canRequest)
        old.run()
        assertFalse(f.state.expired)
        f.reportRead = { ScanUiState() }; f.pump()
        assertTrue(f.state.canRequest)
        f.bind(null)
        assertFalse(f.state.canRequest)
        assertFalse(f.state.saving)
        f.pump()
        assertFalse(f.state.canRequest)
    }

    @Test fun repeatedStartAndSameBindingDoNotCreateExtraWork() {
        val f = Fixture()
        f.ready(); f.start(); f.bind()
        assertEquals(1, f.workers.size)
        assertTrue(f.worker.tasks.isEmpty())
    }

    @Test fun duplicateRequestsCommitOnceAndRefreshConfirmedPending() {
        val f = Fixture()
        f.ready(); f.request(); f.request()
        assertTrue(f.state.saving)
        assertEquals(1, f.worker.tasks.size)
        f.pump(); f.pump()
        assertTrue(f.state.pending)
        assertFalse(f.state.saving)
        assertFalse(f.state.canRequest)
    }

    @Test fun queuedSaveIsCancelledOnStopAndRestartCanRequest() {
        val f = Fixture()
        f.ready(); f.request(); f.stop(); f.start(); f.pump()
        assertNull(f.prefs.getString(ScanProtocol.REQUEST, null))
        assertTrue(f.state.canRequest)
    }

    @Test fun invalidationDuringRequestReadPreventsWrite() {
        val f = Fixture()
        f.ready()
        f.reportRead = { f.bind(Any()); ScanUiState() }
        f.request()
        f.worker.tasks.removeFirst().run(); f.main.drain()
        assertNull(f.prefs.getString(ScanProtocol.REQUEST, null))
        f.reportRead = { ScanUiState() }; f.pump()
        assertTrue(f.state.canRequest)
    }

    @Test fun inFlightCommitAcrossRestartBlocksOverlapAndRereads() {
        val f = Fixture()
        val base = InMemoryPreferences()
        var commits = 0
        f.prefs = commits(base) {
            commits++
            f.stop(); f.start(); f.pump(); f.request()
            assertFalse(f.state.canRequest)
            true
        }
        f.ready(); f.request(); f.pump(); f.pump()
        assertEquals(1, commits)
        assertTrue(f.state.pending)
        assertFalse(f.state.saving)
    }

    @Test fun stopWhileReadingOriginalValueCancelsBeforeCommit() {
        val f = Fixture()
        val base = InMemoryPreferences()
        var reads = 0
        f.prefs = object : SharedPreferences by base {
            override fun getString(key: String, defValue: String?): String? {
                // 首次刷新、预约前复核、存储层读取原值。
                if (++reads == 3) { f.stop(); f.start() }
                return base.getString(key, defValue)
            }
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by base.edit() {
                override fun putString(key: String, value: String?): SharedPreferences.Editor = this
                override fun commit(): Boolean = error("过期预约不得提交")
            }
        }
        f.ready(); f.request(); f.pump(); f.pump()
        assertNull(base.getString(ScanProtocol.REQUEST, null))
        assertTrue(f.state.canRequest)
        assertFalse(f.state.saveFailed)
    }

    @Test fun uncertainSaveSurvivesPollingAndRestartUntilNewBinding() {
        val f = Fixture()
        f.prefs = commits(InMemoryPreferences()) { false }
        f.ready(); f.request(); f.pump(); f.pump()
        assertTrue(f.state.requestUncertain)
        assertFalse(f.state.pending)
        assertFalse(f.state.canRequest)
        f.tick(); f.stop(); f.start(); f.pump(); f.bind(); f.tick()
        assertTrue(f.state.requestUncertain)
        f.prefs = InMemoryPreferences()
        f.bind(Any()); f.pump()
        assertFalse(f.state.requestUncertain)
        assertTrue(f.state.canRequest)
    }

    @Test fun uncertainCompletionAfterStopIsRememberedWithoutCallback() {
        val f = Fixture()
        var countAtStop = 0
        f.prefs = commits(InMemoryPreferences()) {
            f.stop(); countAtStop = f.states.size; false
        }
        f.ready(); f.request(); f.pump()
        assertEquals(countAtStop, f.states.size)
        f.start(); f.pump()
        assertTrue(f.state.requestUncertain)
        assertFalse(f.state.canRequest)
    }

    @Test fun newBindingMustReadSuccessfullyBeforeClearingUncertainty() {
        val f = Fixture()
        f.prefs = commits(InMemoryPreferences()) { false }
        f.ready(); f.request(); f.pump(); f.pump()
        val base = InMemoryPreferences()
        f.prefs = object : SharedPreferences by base {
            override fun getString(key: String, defValue: String?): String = error("read failed")
        }
        f.bind(null); f.pump()
        assertTrue(f.state.requestUncertain)
        f.bind(Any()); f.pump()
        assertTrue(f.state.requestUncertain)
        assertFalse(f.state.canRequest)
        f.prefs = base; f.tick()
        assertFalse(f.state.requestUncertain)
        assertTrue(f.state.canRequest)
    }

    @Test fun oldSaveFailureCannotMarkNewBindingFailed() {
        val f = Fixture()
        val old = InMemoryPreferences()
        var writes = 0
        f.prefs = commits(old) {
            writes++
            if (writes == 1) {
                f.prefs = InMemoryPreferences()
                f.bind(Any()); f.pump(); f.request()
                assertFalse(f.state.canRequest)
                false
            } else true
        }
        f.ready(); f.request(); f.pump(); f.pump()
        assertEquals(2, writes)
        assertFalse(f.state.saveFailed)
        assertFalse(f.state.pending)
        assertTrue(f.state.canRequest)
    }

    @Test fun preflightPendingRequestPreventsOverwrite() {
        val f = Fixture()
        f.ready()
        f.prefs.edit().putString(ScanProtocol.REQUEST, "other-request").commit()
        f.request(); f.pump(); f.pump()
        assertEquals("other-request", f.prefs.getString(ScanProtocol.REQUEST, null))
        assertTrue(f.state.pending)
        assertFalse(f.state.saving)
    }

    @Test fun rejectedRefreshDisablesStaleWritableStateAndRecovers() {
        val f = Fixture()
        f.ready(); f.worker.reject = true; f.tick()
        assertFalse(f.state.canRequest)
        f.worker.reject = false; f.tick()
        assertTrue(f.state.canRequest)
    }

    @Test fun restoredFailureAllowsRetryWithoutFalsePending() {
        val f = Fixture()
        var commits = 0
        f.prefs = commits(InMemoryPreferences()) { ++commits > 1 }
        f.ready(); f.request(); f.pump(); f.pump()
        assertTrue(f.state.saveFailed)
        assertTrue(f.state.canRequest)
        assertFalse(f.state.pending)
        f.request(); f.pump(); f.pump()
        assertFalse(f.state.saveFailed)
        assertTrue(f.state.pending)
    }

    @Test fun preferenceReadFailureNeverEnablesRequest() {
        val f = Fixture()
        f.controller.bindSource(f.identity) { throw IllegalStateException("not logged") }
        f.main.drain(); f.start(); f.pump()
        assertFalse(f.state.canRequest)
    }

    @Test fun rejectedSaveReleasesOccupancyAndPollingRecovers() {
        val f = Fixture()
        f.ready(); f.worker.reject = true; f.request()
        assertFalse(f.state.saving)
        assertTrue(f.state.saveFailed)
        f.worker.reject = false; f.tick(); f.request(); f.pump(); f.pump()
        assertTrue(f.state.pending)
    }

    @Test fun rejectedStartCanBeRetriedAndOldTimerCannotRefreshNewWorker() {
        val f = Fixture()
        f.bind(); f.rejectFactory = true; f.start()
        assertTrue(f.state.saveFailed)
        f.rejectFactory = false; f.start(); f.pump()
        assertTrue(f.state.canRequest)
        val timer = f.worker.tick!!
        f.stop(); f.start(); f.pump()
        timer.run(); f.main.drain()
        assertTrue(f.worker.tasks.isEmpty())
    }

    private fun commits(base: SharedPreferences, commit: () -> Boolean): SharedPreferences =
        object : SharedPreferences by base {
            override fun edit(): SharedPreferences.Editor {
                val editor = base.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value); return this
                    }
                    override fun commit(): Boolean { editor.commit(); return commit() }
                }
            }
        }
}
