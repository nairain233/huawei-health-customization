package love.nairain.huawei

import love.nairain.huawei.hook.feature.BottomBadgeState
import org.junit.Assert.*
import org.junit.Test

class BottomBadgeStateTest {
    @Test fun unrelatedNavigationInstancesKeepRequestsAndNeverEnterRestorationState() {
        val state = BottomBadgeState<Any>("verified.MainBottom")
        val item = Any()
        assertTrue(state.update("other.Navigation", item, true))
        assertTrue(state.replay("other.Navigation", item, true))
        assertTrue(state.snapshot().isEmpty())
    }

    @Test fun replayPreservesLatestHostRequestAndIsolatesItems() {
        val state = BottomBadgeState<Any>("verified.MainBottom")
        val first = Any()
        val second = Any()
        assertFalse(state.replay("verified.MainBottom", first, true))
        assertFalse(state.replay("verified.MainBottom", first, false))
        assertTrue(state.snapshot().getValue(first))
        assertFalse(state.update("verified.MainBottom", second, false))
        assertFalse(state.snapshot().getValue(second))
        assertFalse(state.update("verified.MainBottom", first, false))
        state.replay("verified.MainBottom", first, false)
        assertFalse(state.snapshot().getValue(first))
        state.clear()
        assertTrue(state.snapshot().isEmpty())
    }

    @Test fun directHostRedisplayIsRememberedForFailureRestoration() {
        val state = BottomBadgeState<Any>("verified.MainBottom")
        val item = Any()
        state.update("verified.MainBottom", item, false)
        assertFalse(state.replay("verified.MainBottom", item, true))
        assertTrue(state.snapshot().getValue(item))
    }
}
