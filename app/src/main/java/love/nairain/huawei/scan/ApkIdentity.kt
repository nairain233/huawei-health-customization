package love.nairain.huawei.scan

import java.io.File
import java.security.MessageDigest

internal object ApkIdentity {
    /** 长度与分隔符明确界定每份 APK；split 列表顺序变化本身不影响身份。 */
    fun fingerprint(version: String, code: Long, updated: Long, files: List<File>, rules: Int = ScanProtocol.RULES): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun part(value: String) { digest.update(value.toByteArray()); digest.update(0.toByte()) }
        part("com.huawei.health"); part(version); part(code.toString()); part(updated.toString()); part(rules.toString())
        files.sortedBy { it.absolutePath }.forEach { file ->
            part(file.absolutePath); part(file.length().toString())
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(65536)
                while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
