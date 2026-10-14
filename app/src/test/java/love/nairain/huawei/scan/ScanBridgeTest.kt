package love.nairain.huawei.scan

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.Executor

class ScanBridgeTest {
    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun run() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
    private fun report(sequence: Int = 0) = ScanReport("a".repeat(64), "17.0.8.300", 1700008300,
        1, "", UUID.randomUUID().toString(), 1000, sequence = sequence, time = 1000)

    @Test fun endpointRoundTripAndMalformedValues() {
        val endpoint = ScanBridgeProtocol.create()
        assertEquals(endpoint, ScanBridgeProtocol.endpoint(endpoint.encode()))
        assertNotEquals(endpoint, ScanBridgeProtocol.create())
        listOf(null, "", endpoint.encode() + "|extra", "x".repeat(113),
            endpoint.encode().replace('|', '/')).forEach { assertNull(ScanBridgeProtocol.endpoint(it)) }
    }

    @Test fun authenticationRequiresKnownTargetInSameUser() {
        assertTrue(ScanBridgeProtocol.allows(10123, 10123, 10456))
        assertTrue(ScanBridgeProtocol.allows(110123, 110123, 110456))
        assertFalse(ScanBridgeProtocol.allows(10123, null, 10456))
        assertFalse(ScanBridgeProtocol.allows(10123, 10124, 10456))
        assertFalse(ScanBridgeProtocol.allows(110123, 110123, 10456))
        assertFalse(ScanBridgeProtocol.allows(-1, -1, 10456))
        assertFalse(ReportAdmission.allows(110123, 10456, listOf("com.huawei.health")))
    }

    @Test fun framedUnicodeRoundTripAndWrongTokenRejection() {
        val endpoint = ScanBridgeProtocol.create()
        val output = ByteArrayOutputStream()
        ScanBridgeProtocol.write(output, endpoint.token, "报告测试")
        assertEquals("报告测试", ScanBridgeProtocol.read(output.toByteArray().inputStream(), endpoint.token))
        assertThrows(IllegalArgumentException::class.java) {
            ScanBridgeProtocol.read(output.toByteArray().inputStream(), "0".repeat(64))
        }
    }

    @Test fun rejectOversizeBeforeReadingBodyAndRejectOversizeWrites() {
        val token = "a".repeat(64)
        listOf(-1, 0, 65, ScanBridgeProtocol.MAX_BYTES + 1, Int.MAX_VALUE).forEach { size ->
            val bytes = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(size) }.toByteArray()
            assertThrows(IllegalArgumentException::class.java) { ScanBridgeProtocol.read(bytes.inputStream(), token) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScanBridgeProtocol.write(ByteArrayOutputStream(), token, "中".repeat(ScanBridgeProtocol.MAX_BYTES / 2))
        }
    }

    @Test fun truncatedFramesAndTimeoutNeverProduceReport() {
        val output = ByteArrayOutputStream()
        ScanBridgeProtocol.write(output, "a".repeat(64), "report")
        assertThrows(EOFException::class.java) {
            ScanBridgeProtocol.read(ByteArrayInputStream(output.toByteArray().dropLast(1).toByteArray()), "a".repeat(64))
        }
        assertThrows(SocketTimeoutException::class.java) {
            ScanBridgeProtocol.read(object : InputStream() {
                override fun read(): Int = throw SocketTimeoutException()
            }, "a".repeat(64))
        }
    }

    @Test fun twoTransportsShareOrderingAndDuplicateAdmission() {
        var writes = 0
        val inbox = ScanReportInbox({ null }) { writes++; true }
        val first = report()
        assertTrue(inbox.accept(first.encode(), 1000))
        assertFalse(inbox.accept(first.encode(), 1000))
        val complete = first.copy(sequence = 2, checked = ScanProtocol.keys, phase = "complete")
        assertTrue(inbox.accept(complete.encode(), 1000))
        assertFalse(inbox.accept(first.copy(sequence = 1).encode(), 1000))
        assertEquals(2, writes)
        assertEquals(complete, ScanReport.decode(inbox.snapshot()!!))
    }

    @Test fun olderRunCannotOverwriteNewerRunAndNewRunIsAccepted() {
        val inbox = ScanReportInbox({ null }) { true }
        val first = report()
        val next = first.copy(run = UUID.randomUUID().toString(), started = 2000, time = 2000)
        assertTrue(inbox.accept(first.encode(), 2000))
        assertTrue(inbox.accept(next.encode(), 2000))
        assertFalse(inbox.accept(first.copy(sequence = 5).encode(), 2000))
    }

    @Test fun failedCommitDoesNotConfirmPreferenceMemoryAndCanRetry() {
        var memory: String? = null
        var succeeds = false
        val inbox = ScanReportInbox({ memory }) { memory = it; succeeds }
        val report = report().encode()
        assertThrows(IllegalStateException::class.java) { inbox.accept(report, 1000) }
        assertNotNull(memory)
        assertNull(inbox.snapshot())
        succeeds = true
        assertTrue(inbox.accept(report, 1000))
        assertEquals(report, inbox.snapshot())
    }

    @Test fun futureTimeWrongRulesAndBadJsonNeverWrite() {
        val inbox = ScanReportInbox({ null }) { error("Unexpected write") }
        assertThrows(IllegalArgumentException::class.java) { inbox.accept(report().copy(rules = 99).encode(), 1000) }
        assertThrows(IllegalArgumentException::class.java) { inbox.accept(report().copy(time = 32000).encode(), 1000) }
        assertThrows(Exception::class.java) { inbox.accept("{}", 1000) }
    }

    @Test fun rebindingSkipsQueuedOldSessionAndOldOwnerCannotStopNewOne() {
        val queue = Queue()
        val opened = mutableListOf<Any>()
        var closes = 0
        val lifecycle = ScanBridgeLifecycle(queue, { binding, _ -> opened += binding; AutoCloseable { closes++ } }, { throw it })
        val oldOwner = Any(); val newOwner = Any(); val oldBinding = Any(); val newBinding = Any()
        lifecycle.start(oldOwner, oldBinding)
        lifecycle.start(newOwner, newBinding)
        lifecycle.stop(oldOwner)
        queue.run()
        assertEquals(listOf(newBinding), opened)
        assertEquals(0, closes)
        lifecycle.bind(newOwner, newBinding)
        queue.run()
        assertEquals(1, opened.size)
        lifecycle.bind(newOwner, null)
        assertEquals(1, closes)
    }

    @Test fun lateOpenAfterStopClosesOnlyItsOwnHandle() {
        val queue = Queue()
        val oldOwner = Any(); val newOwner = Any()
        var opens = 0; var closes = 0
        lateinit var lifecycle: ScanBridgeLifecycle
        lifecycle = ScanBridgeLifecycle(queue, { _, active ->
            opens++
            if (opens == 1) {
                lifecycle.stop(oldOwner)
                assertFalse(active())
                lifecycle.start(newOwner, Any())
            }
            AutoCloseable { closes++ }
        }, { throw it })
        lifecycle.start(oldOwner, Any())
        queue.run()
        assertEquals(2, opens)
        assertEquals(1, closes)
        lifecycle.stop(oldOwner)
        assertEquals(1, closes)
        lifecycle.stop(newOwner)
        assertEquals(2, closes)
    }

    @Test fun failedOpenInvalidatesSessionAndReconnectCreatesNewOne() {
        val queue = Queue(); val owner = Any()
        var failures = 0; var closes = 0; var attempts = 0
        var active: (() -> Boolean)? = null
        val lifecycle = ScanBridgeLifecycle(queue, { _, check ->
            active = check
            if (++attempts == 1) throw IllegalStateException()
            AutoCloseable { closes++ }
        }, { failures++ })
        lifecycle.start(owner, Any()); queue.run()
        assertFalse(active!!())
        lifecycle.bind(owner, Any()); queue.run()
        assertTrue(requireNotNull(active).invoke())
        assertEquals(1, failures)
        lifecycle.stop(owner)
        assertEquals(1, closes)
    }

    @Test fun openingPageAfterScanReplaysLatestAndCoalescesProgress() {
        val queue = Queue(); val sent = mutableListOf<String>()
        val latest = LatestScanReport(queue, { sent += it }, { throw it })
        latest.update("running"); latest.request()
        latest.update("complete"); latest.request()
        assertEquals(1, queue.tasks.size)
        queue.run()
        assertEquals(listOf("complete"), sent)
        latest.request(); queue.run()
        assertEquals(listOf("complete", "complete"), sent)
    }

    @Test fun failureAndEndpointChangeDuringSendDoNotLoseLatestReport() {
        val queue = Queue(); val sent = mutableListOf<String>(); var failures = 0
        lateinit var latest: LatestScanReport
        latest = LatestScanReport(queue, {
            sent += it
            if (sent.size == 1) {
                latest.update("complete"); latest.request()
                throw SocketTimeoutException()
            }
        }, { failures++ })
        latest.update("running"); latest.request(); queue.run()
        assertEquals(listOf("running", "complete"), sent)
        assertEquals(1, failures)
        latest.request(); queue.run()
        assertEquals(3, sent.size)
    }
    @Test fun stalePreferencesCannotClearNewlyPublishedEndpoint() {
        val publication = ScanBridgePublication()
        val oldOwner = Any(); val newOwner = Any()
        val oldEndpoint = ScanBridgeProtocol.create(); val newEndpoint = ScanBridgeProtocol.create()
        val oldPrefs = love.nairain.huawei.ScriptedPreferences()
        val newPrefs = love.nairain.huawei.ScriptedPreferences()
        publication.publish(oldOwner, oldPrefs, oldEndpoint)
        publication.publish(newOwner, newPrefs, newEndpoint)
        // 模拟旧服务仍缓存旧端点；清理甚至不应写入这个陈旧偏好对象。
        publication.clear(oldOwner, oldPrefs, oldEndpoint)
        assertEquals(oldEndpoint.encode(), oldPrefs.getString(ScanBridgeProtocol.ENDPOINT, null))
        assertEquals(newEndpoint.encode(), newPrefs.getString(ScanBridgeProtocol.ENDPOINT, null))
        publication.clear(newOwner, newPrefs, newEndpoint)
        assertNull(newPrefs.getString(ScanBridgeProtocol.ENDPOINT, null))
    }

    @Test fun failedPublicationCanCleanItsPartialPreferenceWrite() {
        val publication = ScanBridgePublication(); val owner = Any()
        val endpoint = ScanBridgeProtocol.create()
        val prefs = love.nairain.huawei.ScriptedPreferences(emptyMap(), love.nairain.huawei.CommitAction.RETURN_FALSE)
        assertThrows(IllegalStateException::class.java) { publication.publish(owner, prefs, endpoint) }
        assertEquals(endpoint.encode(), prefs.getString(ScanBridgeProtocol.ENDPOINT, null))
        publication.clear(owner, prefs, endpoint)
        assertNull(prefs.getString(ScanBridgeProtocol.ENDPOINT, null))
    }
}