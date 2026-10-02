package love.nairain.huawei

import love.nairain.huawei.hook.util.LayoutCallbackScope
import org.junit.Assert.*
import org.junit.Test

class LayoutCallbackScopeTest {
    @Test fun activationIsRequiredAndClosingPendingInstallationDiscardsReplay() {
        var calls = 0
        val scope = LayoutCallbackScope { fail(it) }
        scope.afterActivation { calls++ }
        scope.run { calls++ }
        assertEquals(0, calls)
        scope.close()
        scope.activate()
        scope.run { calls++ }
        assertEquals(0, calls)
        assertFalse(scope.isActive)
    }

    @Test fun repeatedActivationDoesNotRepeatReplayAndQueuedWorkHonorsCancellation() {
        val queue = ArrayDeque<() -> Unit>()
        var calls = 0
        val scope = LayoutCallbackScope { fail(it) }
        scope.afterActivation { queue += { scope.run { calls++ } } }
        scope.activate()
        scope.activate()
        assertEquals(1, queue.size)
        scope.close()
        queue.removeFirst().invoke()
        assertEquals(0, calls)
    }

    @Test fun failedListenerDoesNotInterruptIndependentSourceAndLogsOnlyTypeOnce() {
        val errors = mutableListOf<String>()
        val failed = LayoutCallbackScope(errors::add).apply { activate() }
        val healthy = LayoutCallbackScope { fail(it) }.apply { activate() }
        var calls = 0
        listOf(failed, healthy).forEach { scope ->
            scope.run { if (scope === failed) error("private host value") else calls++ }
        }
        failed.run { error("should never be called") }
        healthy.run { calls++ }
        assertEquals(2, calls)
        assertEquals(listOf("IllegalStateException"), errors)
        assertFalse(failed.isActive)
        assertTrue(healthy.isActive)
    }

    @Test fun completedTasksUnregisterAndCleanupFailuresDoNotBlockOtherRemovals() {
        val removed = mutableListOf<Int>()
        val errors = mutableListOf<String>()
        val scope = LayoutCallbackScope(errors::add)
        val unregister = scope.onClose { fail("completed task must no longer be retained") }
        unregister()
        scope.onClose { removed += 1 }
        scope.onClose { removed += 2; error("private cleanup failure") }
        scope.activate()
        scope.close()
        scope.close()
        assertEquals(listOf(2, 1), removed)
        assertEquals(listOf("IllegalStateException"), errors)
    }

    @Test fun cleanupRegisteredAfterClosingIsImmediatelyReleased() {
        var released = 0
        val scope = LayoutCallbackScope { fail(it) }
        scope.close()
        scope.onClose { released++ }
        assertEquals(1, released)
    }

    @Test fun diagnosticFailureDoesNotEscapeIntoHostCallback() {
        val scope = LayoutCallbackScope { error("diagnostic service unavailable") }.apply { activate() }
        scope.run { throw IllegalArgumentException("private callback value") }
        assertFalse(scope.isActive)
    }
}
