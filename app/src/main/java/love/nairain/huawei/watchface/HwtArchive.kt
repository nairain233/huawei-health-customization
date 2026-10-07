package love.nairain.huawei.watchface

import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.SAXException
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/** 只解包用户选择的本地包；不修改表盘二进制、描述或签名。 */
internal object HwtArchive {
    const val PAYLOAD = "com.huawei.watchface"
    const val MAX_ARCHIVE = 64L * 1024 * 1024
    private const val MAX_EXPANDED = 128L * 1024 * 1024
    private const val MAX_ENTRIES = 2048

    data class Description(val version: String, val screen: String)

    fun copy(input: InputStream, destination: File, limit: Long = MAX_ARCHIVE): Long {
        val crc = java.util.zip.CRC32()
        destination.outputStream().use { output ->
            val buffer = ByteArray(32 * 1024)
            var size = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                require(size <= limit) { "File limit" }
                crc.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
            require(size > 0) { "Empty file" }
        }
        return crc.value
    }

    fun extract(archive: File, directory: File): Description {
        require(archive.length() in 1..MAX_ARCHIVE)
        require(!directory.exists() && directory.mkdirs())
        val root = directory.canonicalFile.toPath()
        var expanded = 0L
        val names = HashSet<String>()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            var count = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                require(++count <= MAX_ENTRIES)
                val name = entry.name
                require(name.isNotBlank() && '\\' !in name && ':' !in name && !name.startsWith('/'))
                require(name.trimEnd('/').split('/').none { it.isEmpty() || it == ".." || it == "." })
                require(names.add(name.trimEnd('/'))) { "Duplicate entry" }
                val file = File(directory, name).canonicalFile
                require(file.toPath().startsWith(root) && file.toPath() != root)
                if (entry.isDirectory) {
                    require(file.isDirectory || file.mkdirs())
                } else {
                    val parent = checkNotNull(file.parentFile)
                    require(parent.isDirectory || parent.mkdirs())
                    require(entry.size in 0..MAX_ARCHIVE)
                    expanded += entry.size
                    require(expanded <= MAX_EXPANDED)
                    if (entry.size == 0L) {
                        require(file.createNewFile())
                    } else zip.getInputStream(entry).use { require(copy(it, file, entry.size) == entry.crc) }
                    require(file.length() == entry.size)
                }
            }
        }
        val payload = File(directory, PAYLOAD)
        val description = File(directory, "description.xml")
        require(payload.isFile && payload.length() in 1..MAX_ARCHIVE)
        require(description.isFile && description.length() in 1..(256L * 1024))
        validateNestedPayload(payload)
        return parseDescription(description)
    }

    private fun validateNestedPayload(payload: File) {
        val zipped = payload.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
        if (!zipped) return
        // 宿主 b2 会再次解开载荷，必须在调用前限制内层 ZIP 的膨胀和重复条目。
        ZipFile(payload).use { zip ->
            var total = 0L
            var count = 0
            val names = HashSet<String>()
            val entries = zip.entries()
            val buffer = ByteArray(32 * 1024)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                require(++count <= MAX_ENTRIES && names.add(entry.name))
                require(entry.size in 0..MAX_ARCHIVE)
                total += entry.size
                require(total <= MAX_EXPANDED)
                var actual = 0L
                val crc = java.util.zip.CRC32()
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        actual += read
                        require(actual <= entry.size)
                        crc.update(buffer, 0, read)
                    }
                }
                require(actual == entry.size && crc.value == entry.crc)
            }
        }
    }

    private fun parseDescription(file: File): Description {
        val bytes = file.readBytes()
        // Android 的 Harmony JAXP 不支持 Xerces 的禁用 DTD feature。先严格解码并拒绝
        // DTD/实体声明，再以字符流解析，避免 JVM 测试通过但 Android 始终拒绝所有包。
        val charset = when {
            bytes.size >= 2 && ((bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) || bytes[1] == 0.toByte()) -> Charsets.UTF_16LE
            bytes.size >= 2 && ((bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()) || bytes[0] == 0.toByte()) -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val xml = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) throw SAXException("DTD forbidden")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw SAXException("External entity") }
            setErrorHandler(object : DefaultHandler() {
                override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
                override fun error(e: org.xml.sax.SAXParseException) { throw e }
            })
        }
        val document = builder.parse(InputSource(StringReader(xml)))
        fun value(tag: String): String {
            val nodes = document.documentElement.getElementsByTagName(tag)
            require(nodes.length == 1)
            return nodes.item(0).textContent.trim().also { require(it.isNotEmpty() && it.length <= 80) }
        }
        return Description(value("version"), value("screen"))
    }

    fun compatible(version: String, maximum: String): Boolean {
        fun parse(value: String): List<Int>? {
            val parts = value.split('.')
            if (parts.size < 2) return null
            return parts.take(2).map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        }
        val v = parse(version) ?: return false
        val m = parse(maximum) ?: return false
        return v[0] == m[0] && v[1] <= m[1]
    }

    /** 仅清理本功能创建的直接子目录，拒绝符号链接或目录逃逸。 */
    fun clean(root: File, child: File) {
        require(child.canonicalFile.parentFile == root.canonicalFile)
        require(child.name.matches(Regex("job-[a-zA-Z0-9-]+")))
        if (child.exists()) check(child.deleteRecursively()) { "Cleanup failed" }
    }
}
