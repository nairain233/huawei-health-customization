package love.nairain.huawei.settings

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/** 新目录不读取旧 files/shared_prefs，也不随宿主云备份上传。 */
internal object HostStorage {
    fun directory(context: Context): File = File(context.noBackupFilesDir, "huawei_trim")
    fun scanDirectory(context: Context): File = File(directory(context), "scan")
}

internal class AtomicHostConfigStorage(context: Context) : HostConfigStorage {
    private val file = AtomicFile(File(HostStorage.directory(context), "config.json"))
    override fun read(): String? {
        val input = try { file.openRead() } catch (error: FileNotFoundException) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
            throw error
        }
        return input.bufferedReader().use { reader ->
            val chars = CharArray(HostConfiguration.MAX_CHARS + 1)
            var count = 0
            while (count < chars.size) {
                val read = reader.read(chars, count, chars.size - count)
                if (read < 0) break
                count += read
            }
            require(count <= HostConfiguration.MAX_CHARS)
            String(chars, 0, count)
        }
    }
    override fun write(raw: String) {
        require(raw.length <= HostConfiguration.MAX_CHARS)
        val parent = requireNotNull(file.baseFile.parentFile)
        check(parent.isDirectory || parent.mkdirs())
        val output = file.startWrite()
        try {
            output.write(raw.toByteArray(Charsets.UTF_8))
            output.fd.sync()
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
