package love.nairain.huawei.scan

import love.nairain.huawei.InMemoryPreferences
import love.nairain.huawei.app.ScanUiState
import love.nairain.huawei.config.SettingsKeys
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class ScanProtocolTest {
    private fun report() = ScanReport("a".repeat(64), "17.0.7.310", 1700007310, 10, "request", "11111111-1111-1111-1111-111111111111", 100, time = 100)

    @Test fun countsOnlyLayoutChoicesAndKeepsDisabledChoices() {
        assertEquals(65, ScanProtocol.keys.size)
        assertFalse(ScanProtocol.keys.contains(SettingsKeys.ENABLED))
        assertFalse(ScanProtocol.keys.contains(SettingsKeys.HIDE_LAUNCHER_ICON))
        val source = report().copy(checked = ScanProtocol.keys, matched = setOf(SettingsKeys.MINE_FAMILY), phase = "complete")
        assertEquals(source, ScanReport.decode(source.encode()))
    }

    @Test fun acceptsFingerprintTransitionButRejectsOldRunsAndTerminalUpdates() {
        val first = report().copy(identity = "pending")
        val identified = report().copy(sequence = 1)
        assertTrue(identified.follows(first))
        assertFalse(first.follows(identified))
        assertFalse(identified.follows(identified))
        val complete = identified.copy(sequence = 2, phase = "complete", checked = ScanProtocol.keys)
        assertTrue(complete.follows(identified))
        assertFalse(complete.copy(sequence = 3).follows(complete))
        assertFalse(identified.copy(run = "22222222-2222-2222-2222-222222222222", started = 99).follows(complete))
        assertTrue(identified.copy(run = "22222222-2222-2222-2222-222222222222", started = 101).follows(complete))
    }

    @Test fun rejectsInvalidPayloadsAndFalseCompletion() {
        listOf(
            report().copy(matched = setOf("hide.health.unknown")),
            report().copy(matched = setOf(SettingsKeys.MINE_FAMILY)),
            report().copy(phase = "complete"),
            report().copy(identity = "not-an-apk-fingerprint"),
            report().copy(error = "private intent contents"),
        ).forEach { invalid -> assertTrue(runCatching { ScanReport.decode(invalid.encode()) }.isFailure) }
        assertTrue(runCatching { ScanReport.decode("x".repeat(32769)) }.isFailure)
        assertTrue(runCatching { ScanReport.decode("{") }.isFailure)
    }

    @Test fun admitsOnlyOwnOrHostUid() {
        assertTrue(ReportAdmission.allows(10, 10, emptyList()))
        assertTrue(ReportAdmission.allows(11, 10, listOf("com.huawei.health")))
        assertFalse(ReportAdmission.allows(11, 10, emptyList()))
        assertFalse(ReportAdmission.allows(11, 10, listOf("com.huawei.health.fake")))
    }

    @Test fun rescanFailureRestoresOptimisticLocalWrite() {
        val base = InMemoryPreferences(mapOf(ScanProtocol.REQUEST to "old", SettingsKeys.ENABLED to true))
        val failing = object : SharedPreferences by base {
            override fun edit(): SharedPreferences.Editor {
                val editor = base.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                    override fun commit(): Boolean { editor.commit(); return false }
                }
            }
        }
        assertEquals(ScanRequestOutcome.UNCERTAIN, ScanRequests.save(failing, "new"))
        assertEquals("old", base.getString(ScanProtocol.REQUEST, ""))
        assertTrue(base.getBoolean(SettingsKeys.ENABLED, false))
        assertEquals(ScanRequestOutcome.SUCCESS, ScanRequests.save(base, "new"))
        assertEquals("new", base.getString(ScanProtocol.REQUEST, ""))
    }

    @Test fun onlyActionableStatesEnableRescan() {
        val ready = ScanUiState(writable = true)
        assertTrue(ready.canRequest)
        assertFalse(ready.copy(pending = true).canRequest)
        assertFalse(ready.copy(saving = true).canRequest)
        assertFalse(ready.copy(report = report()).canRequest)
        assertTrue(ready.copy(report = report(), uncertain = true).canRequest)
        assertFalse(ready.copy(writable = false).canRequest)
    }
}
