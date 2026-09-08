package love.nairain.huawei.scan

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ScanCacheTest {
    private val resolution = LayoutResolution(emptySet(), emptySet(), emptyMap(), setOf("Lsample/Target;->run()V"), "manager",
        ScanProtocol.keys.associateWith { "unresolved" })

    @Test fun cachedNegativeResultsAvoidRescanningUntilIdentityOrRequestChanges() {
        val raw = ScanCache.encode("apk1", "request1", resolution)
        val checked = mutableListOf<String>()
        assertEquals(resolution, ScanCache.decode(raw, "apk1", "request1", checked::add))
        assertEquals(resolution.descriptors.toList(), checked)
        assertTrue(runCatching { ScanCache.decode(raw, "apk2", "request1") {} }.isFailure)
        assertTrue(runCatching { ScanCache.decode(raw, "apk1", "request2") {} }.isFailure)
    }

    @Test fun corruptOrUnresolvableCacheIsRejected() {
        val raw = ScanCache.encode("apk1", "request1", resolution)
        val damaged = JSONObject(raw).put("resolution", resolution.encode().replace("manager", "broken")).toString()
        assertTrue(runCatching { ScanCache.decode(damaged, "apk1", "request1") {} }.isFailure)
        assertTrue(runCatching { ScanCache.decode("{", "apk1", "request1") {} }.isFailure)
        assertTrue(runCatching { ScanCache.decode(raw, "apk1", "request1") { throw NoSuchMethodException() } }.isFailure)
    }
}
