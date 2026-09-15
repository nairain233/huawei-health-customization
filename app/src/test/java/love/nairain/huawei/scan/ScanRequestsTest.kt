package love.nairain.huawei.scan

import android.content.SharedPreferences
import love.nairain.huawei.InMemoryPreferences
import org.junit.Assert.*
import org.junit.Test

class ScanRequestsTest {
    private class Store(initial: Map<String, Any?> = emptyMap()) {
        val base = InMemoryPreferences(initial)
        var writes = 0
        var failRead = false
        var failContains = false
        var commit: (Int) -> Boolean = { true }
        val prefs = object : SharedPreferences by base {
            override fun contains(key: String): Boolean {
                if (failContains) error("private error must not be logged")
                return base.contains(key)
            }
            override fun getString(key: String, defValue: String?): String? {
                if (failRead) error("unavailable")
                return base.getString(key, defValue)
            }
            override fun edit(): SharedPreferences.Editor {
                val editor = base.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value); return this
                    }
                    override fun commit(): Boolean {
                        editor.commit() // 模拟远端确认前已更新的进程内缓存。
                        return commit(++writes)
                    }
                }
            }
        }
    }

    @Test fun successCommitsOnlyOnce() {
        val s = Store()
        assertEquals(ScanRequestOutcome.SUCCESS, ScanRequests.save(s.prefs, "new"))
        assertEquals(1, s.writes)
        assertEquals("new", s.base.getString(ScanProtocol.REQUEST, null))
    }

    @Test fun falseCommitRestoresExistingValueAndUnrelatedKeys() {
        val s = Store(mapOf(ScanProtocol.REQUEST to "old", "other" to true))
        s.commit = { it == 2 }
        assertEquals(ScanRequestOutcome.RESTORED, ScanRequests.save(s.prefs, "new"))
        assertEquals(2, s.writes)
        assertEquals("old", s.base.getString(ScanProtocol.REQUEST, null))
        assertTrue(s.base.getBoolean("other", false))
    }

    @Test fun thrownCommitRemovesOriginallyAbsentKey() {
        val s = Store()
        s.commit = { if (it == 1) error("failure") else true }
        assertEquals(ScanRequestOutcome.RESTORED, ScanRequests.save(s.prefs, "new"))
        assertEquals(2, s.writes)
        assertFalse(s.base.contains(ScanProtocol.REQUEST))
    }

    @Test fun failedRollbackIsUncertainEvenWhenCacheLooksRestored() {
        val s = Store(mapOf(ScanProtocol.REQUEST to "old"))
        s.commit = { false }
        assertEquals(ScanRequestOutcome.UNCERTAIN, ScanRequests.save(s.prefs, "new"))
        assertEquals("old", s.base.getString(ScanProtocol.REQUEST, null))
        assertEquals(2, s.writes)
    }

    @Test fun thrownRollbackIsUncertain() {
        val s = Store()
        s.commit = { if (it == 2) error("rollback") else false }
        assertEquals(ScanRequestOutcome.UNCERTAIN, ScanRequests.save(s.prefs, "new"))
        assertEquals(2, s.writes)
    }

    @Test fun readFailureDoesNotWrite() {
        val s = Store()
        s.failRead = true
        assertEquals(ScanRequestOutcome.RESTORED, ScanRequests.save(s.prefs, "new"))
        assertEquals(0, s.writes)
    }

    @Test fun presenceReadFailureDoesNotWrite() {
        val s = Store()
        s.failContains = true
        assertEquals(ScanRequestOutcome.RESTORED, ScanRequests.save(s.prefs, "new"))
        assertEquals(0, s.writes)
    }

    @Test fun invalidatedRequestDoesNotCommitOrChangeOriginalValue() {
        val s = Store(mapOf(ScanProtocol.REQUEST to "old"))
        assertEquals(ScanRequestOutcome.RESTORED, ScanRequests.save(s.prefs, "new") { false })
        assertEquals(0, s.writes)
        assertEquals("old", s.base.getString(ScanProtocol.REQUEST, null))
    }
}
