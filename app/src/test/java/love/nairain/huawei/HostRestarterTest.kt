package love.nairain.huawei

import love.nairain.huawei.hook.HookInstallPolicy
import love.nairain.huawei.settings.HostRestarter
import love.nairain.huawei.settings.HostSettingsNotice
import love.nairain.huawei.settings.HostSettingsState
import org.junit.Assert.*
import org.junit.Test

class HostRestarterTest {
    private class Fixture {
        var packageName = HookInstallPolicy.TARGET_PACKAGE
        var processName = HookInstallPolicy.TARGET_PACKAGE
        var confirmed = true
        var busy = false
        var validLauncher = true
        var launchFailure = false
        val events = mutableListOf<String>()
        val diagnostics = mutableListOf<String>()
        fun create() = HostRestarter(packageName, { processName }, { confirmed }, {
            events += "launch"
            if (launchFailure) throw SecurityException("sensitive host message")
            validLauncher
        }, { events += "terminate" }, diagnostics::add).also { it.setWatchFaceBusyCheck { busy } }
        fun request(restarter: HostRestarter) = restarter.restart { events += "notice" }
    }

    @Test fun onlyConfirmedIdleConfigurationAllowsRestart() {
        HostSettingsNotice.entries.forEach { notice ->
            val accepted = notice in setOf(HostSettingsNotice.READY, HostSettingsNotice.SAVED, HostSettingsNotice.SCHEDULED)
            assertEquals(notice.name, accepted, HostSettingsState(writable = true, notice = notice).canRestart)
            assertFalse(HostSettingsState(writable = false, notice = notice).canRestart)
        }
        val f = Fixture().apply { confirmed = false }
        val restarter = f.create()
        assertFalse(restarter.available)
        assertEquals(HostRestarter.Result.CONFIGURATION_BLOCKED, f.request(restarter))
        assertTrue(f.events.isEmpty())
        f.confirmed = true
        assertTrue(restarter.available)
        f.request(restarter)
        assertEquals(listOf("notice", "launch", "terminate"), f.events)
    }

    @Test fun launchPrecedesTerminationAndRepeatedClicksAreIgnored() {
        val f = Fixture()
        val restarter = f.create()
        assertEquals(HostRestarter.Result.REQUESTED, f.request(restarter))
        assertFalse(restarter.available)
        assertEquals(HostRestarter.Result.ALREADY_REQUESTED, f.request(restarter))
        assertEquals(listOf("notice", "launch", "terminate"), f.events)
    }

    @Test fun foreignPackageAndSubprocessCannotLaunchOrTerminate() {
        listOf("foreign.app" to HookInstallPolicy.TARGET_PACKAGE,
            HookInstallPolicy.TARGET_PACKAGE to "com.huawei.health:worker").forEach { (pkg, process) ->
            val f = Fixture().apply { packageName = pkg; processName = process }
            assertEquals(HostRestarter.Result.FAILED, f.request(f.create()))
            assertTrue(f.events.isEmpty())
            assertEquals(listOf("Host restart rejected: process_guard"), f.diagnostics)
        }
    }

    @Test fun missingLauncherAndLaunchFailureKeepProcessAndPermitRetry() {
        listOf(false, true).forEach { failure ->
            val f = Fixture().apply { validLauncher = failure; launchFailure = failure }
            val restarter = f.create()
            assertEquals(HostRestarter.Result.FAILED, f.request(restarter))
            assertFalse("terminate" in f.events)
            assertTrue(restarter.available)
            assertFalse(f.diagnostics.joinToString().contains("sensitive"))
            f.validLauncher = true; f.launchFailure = false
            assertEquals(HostRestarter.Result.REQUESTED, f.request(restarter))
            assertEquals(1, f.events.count { it == "terminate" })
        }
    }

    @Test fun clickRechecksWatchFaceStateAndProbeFailureBlocksRestart() {
        val f = Fixture()
        val restarter = f.create()
        assertTrue(restarter.available)
        f.busy = true // 按钮绘制后进入表盘任务，点击时仍须拦截。
        assertEquals(HostRestarter.Result.WATCH_FACE_BUSY, f.request(restarter))
        assertTrue(f.events.isEmpty())
        restarter.setWatchFaceBusyCheck { throw IllegalStateException("uncertain") }
        assertFalse(restarter.available)
        assertEquals(HostRestarter.Result.WATCH_FACE_BUSY, f.request(restarter))
        restarter.setWatchFaceBusyCheck { false }
        assertTrue(restarter.available)
    }

    @Test fun pageCloseDoesNotReleaseBusyTaskAndOldCloseCannotRemoveNewObserver() {
        val f = Fixture().apply { busy = true }
        val restarter = f.create()
        var oldCalls = 0
        var newCalls = 0
        val old = restarter.observe { oldCalls++ }
        old.close()
        assertFalse(restarter.available) // 页面关闭，取消尚未确认的任务仍拦截重启。
        val current = restarter.observe { newCalls++ }
        old.close()
        f.busy = false // 原生会话安全释放后才恢复。
        restarter.watchFaceStateChanged()
        assertTrue(restarter.available)
        assertEquals(1, oldCalls)
        assertEquals(2, newCalls)
        current.close()
        restarter.watchFaceStateChanged()
        assertEquals(2, newCalls)
    }

    @Test fun reentrantClickDuringLaunchCannotScheduleSecondRestart() {
        var launches = 0
        var terminations = 0
        lateinit var restarter: HostRestarter
        restarter = HostRestarter(HookInstallPolicy.TARGET_PACKAGE, { HookInstallPolicy.TARGET_PACKAGE }, { true }, {
            launches++
            assertEquals(HostRestarter.Result.ALREADY_REQUESTED, restarter.restart {})
            true
        }, { terminations++ }, {})
        assertEquals(HostRestarter.Result.REQUESTED, restarter.restart {})
        assertEquals(1, launches)
        assertEquals(1, terminations)
    }

    @Test fun observerFailureCannotInterruptWatchFaceStateChanges() {
        val f = Fixture()
        val restarter = f.create()
        val subscription = restarter.observe { throw IllegalStateException("sensitive") }
        restarter.watchFaceStateChanged()
        assertTrue(restarter.available)
        assertFalse(f.diagnostics.joinToString().contains("sensitive"))
        assertEquals(2, f.diagnostics.size)
        subscription.close()
    }
}
