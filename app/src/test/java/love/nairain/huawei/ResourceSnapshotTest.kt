package love.nairain.huawei

import love.nairain.huawei.hook.util.ResourceIdResolver
import org.junit.Assert.*
import org.junit.Test

class ResourceSnapshotTest {
    @Test fun compressedNamesUseCurrentIdsAndIndependentApkSnapshots() {
        val old = ResourceIdResolver(mapOf("id/container" to 51, "string/title" to 99))
        val current = ResourceIdResolver(mapOf("id/container" to 72, "string/title" to 51))
        assertEquals(51, old.id("container"))
        assertEquals(72, current.id("container"))
        assertEquals("container", current.name(72))
        assertNull(current.name(51))
        assertEquals(0, current.id("missing"))
    }

    @Test fun conflictingViewIdentitiesCannotAuthorizeTextContainers() {
        val resolver = ResourceIdResolver(mapOf("id/one" to 42, "id/two" to 42))
        assertNull(resolver.name(42))
    }
}
