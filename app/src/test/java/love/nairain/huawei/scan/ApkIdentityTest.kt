package love.nairain.huawei.scan

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkIdentityTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun cacheIdentityChangesForCodeUpdatesRulesAndEverySplit() {
        val base = temp.newFile("base.apk").apply { writeText("base") }
        val split = temp.newFile("split.apk").apply { writeText("split") }
        val files = listOf(base, split)
        val old = ApkIdentity.fingerprint("v1", 1, 10, files)
        assertEquals(old, ApkIdentity.fingerprint("v1", 1, 10, files.reversed()))
        assertNotEquals(old, ApkIdentity.fingerprint("v2", 2, 10, files))
        assertNotEquals(old, ApkIdentity.fingerprint("v1", 1, 11, files))
        assertNotEquals(old, ApkIdentity.fingerprint("v1", 1, 10, files, rules = 2))
        assertNotEquals(old, ApkIdentity.fingerprint("v1", 1, 10, listOf(base)))
        split.writeText("other")
        assertNotEquals(old, ApkIdentity.fingerprint("v1", 1, 10, files))
        split.writeText("split")
        assertEquals(old, ApkIdentity.fingerprint("v1", 1, 10, files))
        base.writeText("changed")
        assertNotEquals(old, ApkIdentity.fingerprint("v1", 1, 10, files))
    }
}
