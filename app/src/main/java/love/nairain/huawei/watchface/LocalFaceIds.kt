package love.nairain.huawei.watchface

import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom

/** 每次导入都分配新身份；先 fsync 保留记录，再允许调用设备接口。失败记录也不复用。 */
internal class LocalFaceIds(private val directory: File, private val random: () -> Int = { SecureRandom().nextInt(900_000_000) }) {
    @Synchronized
    fun reserve(existing: Set<String>): String {
        check(directory.isDirectory || directory.mkdirs())
        repeat(128) {
            val value = random()
            require(value in 0 until 900_000_000)
            val id = (100_000_000 + value).toString()
            val record = File(directory, "$id.reserved")
            if (id !in existing && record.createNewFile()) {
                FileOutputStream(record).use { stream ->
                    stream.write("reserved\n".toByteArray(Charsets.US_ASCII))
                    stream.fd.sync()
                }
                return id
            }
        }
        error("ID allocation failed")
    }

    fun owns(id: String): Boolean = id.matches(Regex("[1-9][0-9]{8}")) && File(directory, "$id.reserved").isFile

    fun allocated(): Set<String> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.name.matches(Regex("[1-9][0-9]{8}\\.reserved")) }
        .map { it.name.removeSuffix(".reserved") }.toSet()
}
