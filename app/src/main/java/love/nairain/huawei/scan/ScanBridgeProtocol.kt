package love.nairain.huawei.scan

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID

/** 独立通信组不参与布局配置、预约请求或扫描缓存身份。 */
internal object ScanBridgeProtocol {
    const val GROUP = "scan_report_bridge"
    const val ENDPOINT = "endpoint"
    const val MAX_BYTES = 128 * 1024
    const val TIMEOUT_MS = 2000

    data class Endpoint(val address: String, val token: String) {
        fun encode() = "$address|$token"
    }

    fun create(): Endpoint = Endpoint("huawei-scan-${UUID.randomUUID()}",
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""))

    fun endpoint(raw: String?): Endpoint? {
        if (raw == null || raw.length != 113) return null
        val parts = raw.split('|')
        if (parts.size != 2 || !parts[0].matches(Regex("huawei-scan-[0-9a-f-]{36}")) ||
            !parts[1].matches(Regex("[0-9a-f]{64}"))) return null
        return Endpoint(parts[0], parts[1])
    }

    fun write(output: OutputStream, token: String, report: String) {
        require(token.matches(Regex("[0-9a-f]{64}")))
        val bytes = "$token\n$report".toByteArray(Charsets.UTF_8)
        require(bytes.size in 66..MAX_BYTES)
        DataOutputStream(output).apply { writeInt(bytes.size); write(bytes); flush() }
    }

    fun read(input: InputStream, token: String): String {
        val stream = DataInputStream(input)
        val length = stream.readInt()
        require(length in 66..MAX_BYTES)
        val bytes = ByteArray(length)
        stream.readFully(bytes)
        require(bytes[64] == '\n'.code.toByte() && MessageDigest.isEqual(
            token.toByteArray(Charsets.US_ASCII), bytes.copyOfRange(0, 64)))
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, 65, length - 65)).toString()
    }

    /** expectedUid 必须由接收端包管理器查询，不能取自报文；Android UID 高位为用户空间。 */
    fun allows(uid: Int, expectedUid: Int?, ownUid: Int): Boolean =
        uid >= 0 && expectedUid != null && uid == expectedUid && uid / 100000 == ownUid / 100000
}

/** 两种传输共用已确认快照；commit 失败即使改变了偏好内存，也不能被当作已保存。 */
internal class ScanReportInbox(private val read: () -> String?, private val write: (String) -> Boolean) {
    private var loaded = false
    private var confirmed: String? = null

    @Synchronized fun snapshot(): String? {
        if (!loaded) { confirmed = read(); loaded = true }
        return confirmed
    }

    @Synchronized fun accept(raw: String, now: Long): Boolean {
        val report = ScanReport.decode(raw)
        require(report.rules == ScanProtocol.RULES && report.time <= now + 30_000)
        val previous = snapshot()?.let { runCatching { ScanReport.decode(it) }.getOrNull() }
        if (!report.follows(previous)) return false
        val encoded = report.encode()
        check(write(encoded))
        confirmed = encoded
        return true
    }
}