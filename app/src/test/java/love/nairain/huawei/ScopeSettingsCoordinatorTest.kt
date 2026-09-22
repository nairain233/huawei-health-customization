package love.nairain.huawei

import love.nairain.huawei.app.ScopeRequestCallback
import love.nairain.huawei.app.ScopeService
import love.nairain.huawei.app.ScopeSettingsCoordinator
import love.nairain.huawei.hook.HookInstallPolicy
import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeSettingsCoordinatorTest {
    private val directExecutor = Executor(Runnable::run)

    @Test
    fun bindReadsActualScopeAndPublishesGrantedState() {
        val worker = ScopeQueuedExecutor()
        val coordinator = ScopeSettingsCoordinator(worker, directExecutor)
        coordinator.bind(FakeScopeService(scope = mutableSetOf(HookInstallPolicy.TARGET_PACKAGE)))

        worker.runAll()

        assertTrue(coordinator.currentState().isServiceConnected)
        assertTrue(coordinator.currentState().isGranted)
        assertTrue(coordinator.currentState().writable)
        assertFalse(coordinator.currentState().hasError)
    }

    @Test
    fun requestWaitsForApprovalAndThenRechecksScope() {
        val worker = ScopeQueuedExecutor()
        val service = FakeScopeService()
        val coordinator = ScopeSettingsCoordinator(worker, directExecutor)
        coordinator.bind(service)
        worker.runAll()

        assertTrue(coordinator.setGranted(true))
        assertTrue(coordinator.currentState().isChanging)
        worker.runAll()

        assertEquals(listOf(HookInstallPolicy.TARGET_PACKAGE), service.requestedPackages)
        assertFalse(coordinator.currentState().isGranted)

        service.approve()
        worker.runAll()

        assertTrue(coordinator.currentState().isGranted)
        assertTrue(coordinator.currentState().writable)
        assertFalse(coordinator.currentState().hasError)
    }

    @Test
    fun rejectedRequestKeepsPreviousValueAndAllowsRetry() {
        val worker = ScopeQueuedExecutor()
        val service = FakeScopeService()
        val coordinator = ScopeSettingsCoordinator(worker, directExecutor)
        coordinator.bind(service)
        worker.runAll()

        coordinator.setGranted(true)
        worker.runAll()
        service.reject()
        worker.runAll()

        assertFalse(coordinator.currentState().isGranted)
        assertTrue(coordinator.currentState().hasError)
        assertTrue(coordinator.currentState().writable)
    }

    @Test
    fun removalUsesTheSameTargetAndReflectsActualScope() {
        val worker = ScopeQueuedExecutor()
        val service = FakeScopeService(scope = mutableSetOf(HookInstallPolicy.TARGET_PACKAGE))
        val coordinator = ScopeSettingsCoordinator(worker, directExecutor)
        coordinator.bind(service)
        worker.runAll()

        assertTrue(coordinator.setGranted(false))
        worker.runAll()

        assertEquals(listOf(HookInstallPolicy.TARGET_PACKAGE), service.removedPackages)
        assertFalse(coordinator.currentState().isGranted)
        assertTrue(coordinator.currentState().writable)
    }

    @Test
    fun callbackFromOldBindingCannotReplaceCurrentScopeState() {
        val worker = ScopeQueuedExecutor()
        val first = FakeScopeService()
        val second = FakeScopeService()
        val coordinator = ScopeSettingsCoordinator(worker, directExecutor)
        coordinator.bind(first)
        worker.runAll()
        coordinator.setGranted(true)
        worker.runAll()

        coordinator.bind(second)
        worker.runAll()
        first.approve()
        worker.runAll()

        assertFalse(coordinator.currentState().isGranted)
        assertFalse(second.scope.contains(HookInstallPolicy.TARGET_PACKAGE))
    }
}

private class FakeScopeService(
    val scope: MutableSet<String> = mutableSetOf(),
) : ScopeService {
    var requestedPackages: List<String>? = null
        private set
    var removedPackages: List<String>? = null
        private set
    private var callback: ScopeRequestCallback? = null

    override fun getScope(): List<String> = scope.toList()

    override fun requestScope(packages: List<String>, callback: ScopeRequestCallback) {
        requestedPackages = packages
        this.callback = callback
    }

    override fun removeScope(packages: List<String>) {
        removedPackages = packages
        scope.removeAll(packages.toSet())
    }

    fun approve() {
        scope += HookInstallPolicy.TARGET_PACKAGE
        callback?.onApproved(listOf(HookInstallPolicy.TARGET_PACKAGE))
        callback = null
    }

    fun reject() {
        callback?.onFailed()
        callback = null
    }
}

private class ScopeQueuedExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        tasks.addLast(command)
    }

    fun runAll() {
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
    }
}
