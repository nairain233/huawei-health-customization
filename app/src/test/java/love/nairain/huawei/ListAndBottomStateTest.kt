package love.nairain.huawei

import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.feature.BottomLayoutOrder
import love.nairain.huawei.hook.feature.BottomTabIndexResolver
import love.nairain.huawei.hook.feature.BottomTabStateStore
import love.nairain.huawei.hook.resolver.ListFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListAndBottomStateTest {
    @Test
    fun filteringReturnsShallowCopyAndKeepsUnknownContent() {
        data class Item(val key: String?)
        val hidden = Item("hidden")
        val unknown = Item(null)
        val source = arrayListOf(hidden, unknown)
        val result = ListFilters.copyAndFilter(source, Item::key, mapOf("hidden" to true))
        assertNotSame(source, result)
        assertEquals(listOf(unknown), result)
        assertEquals(listOf(hidden, unknown), source)
    }

    @Test
    fun groupCleanupRemovesLeadingTrailingAndDuplicateDividers() {
        val result = ListFilters.cleanGroups(listOf("|", "a", "|", "|", "b", "|")) { it == "|" }
        assertEquals(listOf("a", "|", "b"), result)
    }

    @Test
    fun emptySectionHeadersAreRemoved() {
        data class Item(val name: String, val divider: Boolean, val titled: Boolean = false)
        val data = listOf(
            Item("data", true, true),
            Item("row", false),
            Item("spacer", true),
            Item("empty", true, true),
        )
        val result = ListFilters.cleanSections(data, Item::divider, Item::titled)
        assertEquals(listOf("data", "row"), result.map(Item::name))
    }

    @Test
    fun bottomStateIsPerInstanceAndRtlOrderIsNormalized() {
        val first = Any()
        val second = Any()
        val state = BottomTabStateStore<Any>()
        state.record(first, 1, true)
        state.record(second, 3, true)
        assertEquals(setOf(1), state.snapshot(first))
        assertEquals(setOf(3), state.snapshot(second))
        state.clear(first)
        assertTrue(state.snapshot(first).isEmpty())
        assertFalse(state.snapshot(second).isEmpty())
        assertEquals(listOf(1, 2, 3), BottomLayoutOrder.arrange(listOf(1, 2, 3), false))
        assertEquals(listOf(3, 2, 1), BottomLayoutOrder.arrange(listOf(1, 2, 3), true))
    }

    @Test
    fun bottomIndexesKeepHuaweiOriginalTabOrder() {
        assertEquals(SettingsKeys.BOTTOM_HEALTH, BottomTabIndexResolver.resolve(0))
        assertEquals(SettingsKeys.BOTTOM_SPORT, BottomTabIndexResolver.resolve(1))
        assertEquals(SettingsKeys.BOTTOM_MEMBER, BottomTabIndexResolver.resolve(2))
        assertEquals(SettingsKeys.BOTTOM_DEVICE, BottomTabIndexResolver.resolve(3))
        assertEquals(SettingsKeys.BOTTOM_MINE, BottomTabIndexResolver.resolve(4))
        assertNull(BottomTabIndexResolver.resolve(5))
    }
}
