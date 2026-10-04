package love.nairain.huawei.watchface

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import love.nairain.huawei.CommitAction
import love.nairain.huawei.ScriptedPreferences
import love.nairain.huawei.completePreferences
import love.nairain.huawei.config.LayoutConfigStore
import love.nairain.huawei.config.LayoutWriteOutcome
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.resolver.LocalWatchFaceTargets
import love.nairain.huawei.scan.ScanProtocol
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalWatchFaceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun idsAvoidDeviceAndPersistedCollisions() {
        val directory = temporary.newFolder("ids")
        val sequence = ArrayDeque(listOf(0, 1, 1, 2))
        val ids = LocalFaceIds(directory) { sequence.removeFirst() }
        assertEquals("100000001", ids.reserve(setOf("100000000")))
        assertEquals("100000002", ids.reserve(emptySet()))
        val restarted = LocalFaceIds(directory) { 3 }
        assertTrue(restarted.owns("100000001"))
        assertEquals(setOf("100000001", "100000002"), restarted.allocated())
        assertFalse(restarted.owns("../100000001"))
        assertFalse(restarted.owns("000000001"))
    }

    @Test fun repeatedImportsAlwaysGetNewIds() {
        var value = 10
        val ids = LocalFaceIds(temporary.newFolder()) { value++ }
        assertNotEquals(ids.reserve(emptySet()), ids.reserve(emptySet()))
    }

    @Test fun idWriteFailureDoesNotReturnAnIdentity() {
        val directory = temporary.newFile()
        assertThrows(IllegalStateException::class.java) { LocalFaceIds(directory).reserve(emptySet()) }
    }

    @Test fun boundedIdSearchDoesNotOverwrite() {
        val ids = LocalFaceIds(temporary.newFolder()) { 0 }
        ids.reserve(emptySet())
        assertThrows(IllegalStateException::class.java) { ids.reserve(emptySet()) }
    }

    @Test fun packageExtractsDescriptionAndPayloadWithoutChangingSource() {
        val archive = archive()
        val original = archive.readBytes()
        val output = File(temporary.root, "unpacked")
        assertEquals(HwtArchive.Description("2.0.1", "HWHD09"), HwtArchive.extract(archive, output))
        assertArrayEquals(byteArrayOf(1, 2, 3), File(output, HwtArchive.PAYLOAD).readBytes())
        assertArrayEquals(original, archive.readBytes())
    }

    @Test fun traversalIsRejectedWithoutWritingOutside() {
        val archive = archive(extra = "../escaped")
        assertThrows(IllegalArgumentException::class.java) { HwtArchive.extract(archive, File(temporary.root, "out")) }
        assertFalse(File(temporary.root, "escaped").exists())
    }

    @Test fun windowsAndAbsolutePathsAreRejected() {
        listOf("C:/bad", "dir\\bad", "/absolute", "a/./bad").forEachIndexed { index, name ->
            assertThrows(IllegalArgumentException::class.java) {
                HwtArchive.extract(archive(extra = name), File(temporary.root, "out$index"))
            }
        }
    }

    @Test fun entitiesAreRejected() {
        val xml = "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///unused'>]><x><version>&e;</version><screen>HWHD09</screen></x>"
        assertThrows(org.xml.sax.SAXException::class.java) {
            HwtArchive.extract(archive(xml), File(temporary.root, "out"))
        }
    }

    @Test fun missingPayloadAndDuplicateMetadataAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            HwtArchive.extract(archive(includePayload = false), File(temporary.root, "out1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HwtArchive.extract(archive("<x><version>2.0</version><version>2.0</version><screen>HWHD09</screen></x>"), File(temporary.root, "out2"))
        }
    }

    @Test fun utf16DescriptionIsSupportedWithoutEnablingDtd() {
        val archive = temporary.newFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("description.xml"))
            zip.write("<x><version>2.0</version><screen>HWHD09</screen></x>".toByteArray(Charsets.UTF_16))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(HwtArchive.PAYLOAD)); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        assertEquals("HWHD09", HwtArchive.extract(archive, File(temporary.root, "out")).screen)
    }

    @Test fun nestedZipIsCheckedBeforeHostDecoder() {
        val nested = java.io.ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("watchface.bin")); zip.write(byteArrayOf(4, 5)); zip.closeEntry()
            }
        }.toByteArray()
        val archive = temporary.newFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("description.xml"))
            zip.write("<x><version>2.0</version><screen>HWHD09</screen></x>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(HwtArchive.PAYLOAD)); zip.write(nested); zip.closeEntry()
        }
        assertEquals("2.0", HwtArchive.extract(archive, File(temporary.root, "out")).version)
    }

    @Test fun emptyAndOversizedStreamsFail() {
        assertThrows(IllegalArgumentException::class.java) {
            HwtArchive.copy(byteArrayOf().inputStream(), temporary.newFile())
        }
        assertThrows(IllegalArgumentException::class.java) {
            HwtArchive.copy(ByteArray(9).inputStream(), temporary.newFile(), 8)
        }
    }

    @Test fun cleanupIsLimitedToOwnedJobs() {
        val root = temporary.newFolder()
        val job = File(root, "job-example").apply { mkdirs() }
        File(job, "payload").writeText("example")
        HwtArchive.clean(root, job)
        assertFalse(job.exists())
        val ids = File(root, "ids").apply { mkdirs() }
        assertThrows(IllegalArgumentException::class.java) { HwtArchive.clean(root, ids) }
        assertTrue(ids.exists())
    }

    @Test fun compatibilityMatchesHostMajorAndMinorRules() {
        assertTrue(HwtArchive.compatible("2.0.1", "2.1.0"))
        assertFalse(HwtArchive.compatible("2.2.0", "2.1.0"))
        assertFalse(HwtArchive.compatible("3.0.0", "2.1.0"))
        assertFalse(HwtArchive.compatible("bad", "2.1"))
        assertFalse(HwtArchive.compatible("2.-1", "2.1"))
    }

    @Test fun transferAndStaleCallbacksCannotClaimSuccess() {
        val state = LocalInstallState("100000001", "1.0.0")
        assertTrue(state.readyToTransfer())
        assertFalse(state.readyToTransfer())
        assertFalse(state.applied())
        assertFalse(state.verify(mapOf(state.id to state.version)))
        assertTrue(state.transferred())
        assertFalse(state.transferred())
        assertTrue(state.applied())
        assertFalse(state.verify(mapOf(state.id to "wrong")))
        assertTrue(state.verify(mapOf(state.id to state.version)))
        assertEquals(LocalInstallState.Phase.SUCCEEDED, state.phase)
        assertFalse(state.fail())
    }

    @Test fun failedSessionCannotResumeOrBecomeSuccessful() {
        val state = LocalInstallState("100000001", "1.0.0")
        state.readyToTransfer()
        assertTrue(state.fail())
        assertFalse(state.transferred())
        assertFalse(state.applied())
        assertFalse(state.verify(mapOf(state.id to state.version)))
        assertFalse(state.fail())
    }

    @Test fun oldApplyCallbackAndQueuedListCannotConfirmNewPhase() {
        val state = LocalInstallState("100000001", "1.0.0")
        val firstApply = state.phase
        state.readyToTransfer()
        state.transferred()
        assertFalse(state.applied(firstApply))
        val oldListPhase = state.phase
        assertTrue(state.applied())
        assertFalse(state.verify(mapOf(state.id to state.version), oldListPhase))
        assertTrue(state.verify(mapOf(state.id to state.version), LocalInstallState.Phase.VERIFYING))
    }

    @Test fun toggleIsOffByDefaultAndIndependentOfLayout() {
        assertFalse(SettingsCatalog.defaults.getValue(SettingsKeys.LOCAL_WATCH_FACE))
        val next = SettingsCatalog.normalizeWrite(SettingsKeys.LOCAL_WATCH_FACE, true, SettingsCatalog.defaults)
        assertTrue(next.getValue(SettingsKeys.LOCAL_WATCH_FACE))
        assertFalse(next.getValue(SettingsKeys.ENABLED))
        assertEquals(96, ScanProtocol.keys.size)
        assertFalse(SettingsKeys.LOCAL_WATCH_FACE in ScanProtocol.keys)
    }

    @Test fun failedToggleWriteRestoresConfirmedState() {
        val initial = completePreferences() + ("service_block.enabled" to true)
        val preferences = ScriptedPreferences(initial, CommitAction.RETURN_FALSE, CommitAction.RETURN_TRUE)
        val saved = LayoutConfigStore().save(preferences, SettingsCatalog.defaults, SettingsKeys.LOCAL_WATCH_FACE, true)
        assertEquals(LayoutWriteOutcome.FAILED_RESTORED, saved.outcome)
        assertEquals(initial, preferences.all)
        assertFalse(saved.values.getValue(SettingsKeys.LOCAL_WATCH_FACE))
    }

    @Test fun onlyExactEnabledVersionIsAccepted() {
        assertTrue(LocalWatchFaceTargets.accepts("17.0.7.320", 1700007320, true))
        assertFalse(LocalWatchFaceTargets.accepts("17.0.7.320", 1700007320, false))
        assertFalse(LocalWatchFaceTargets.accepts("17.0.7.310", 1700007310, true))
        assertFalse(LocalWatchFaceTargets.accepts("17.0.7.320", 1, true))
    }

    private fun archive(
        xml: String = "<HwTheme><version>2.0.1</version><screen>HWHD09</screen></HwTheme>",
        extra: String? = null,
        includePayload: Boolean = true,
    ): File = temporary.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            entry("description.xml", xml.toByteArray())
            if (includePayload) entry(HwtArchive.PAYLOAD, byteArrayOf(1, 2, 3))
            if (extra != null) entry(extra, byteArrayOf(1))
        }
    }
}
