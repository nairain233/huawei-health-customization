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

    @Test fun rule6CacheCannotBeUsedAfter8300Adaptation() {
        val apk = kotlin.io.path.createTempFile().toFile()
        try {
            apk.writeText("same APK content")
            val previous = ApkIdentity.fingerprint("17.0.8.300", 1700008300L, 10, listOf(apk), rules = 6)
            val current = ApkIdentity.fingerprint("17.0.8.300", 1700008300L, 10, listOf(apk))
            assertNotEquals(previous, current)
            val raw = ScanCache.encode(previous, "", resolution)
            assertTrue(runCatching { ScanCache.decode(raw, current, "") {} }.isFailure)
        } finally {
            apk.delete()
        }
    }
}
