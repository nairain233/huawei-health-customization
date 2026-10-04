package love.nairain.huawei.scan

import android.annotation.SuppressLint
import android.content.res.Resources

/** 资源名被压缩时只读取当前 APK 的 R 字段，不复用历史版本的数字 ID。 */
internal class HostResources(private val resources: Resources, private val loader: ClassLoader, private val packageName: String) {
    private val cache = mutableMapOf<String, Int>()

    @SuppressLint("DiscouragedApi")
    fun id(name: String, kind: String): Int = cache.getOrPut("$kind/$name") {
        val direct = resources.getIdentifier(name, kind, packageName)
        val reflected = run {
            val owners = listOf("com.huawei.ui.main.R", "com.huawei.ui.homehealth.R", "com.huawei.health.R")
                .mapNotNull { owner ->
                    try {
                        Class.forName("$owner\$$kind", false, loader)
                    } catch (_: ReflectiveOperationException) { null }
                    catch (_: LinkageError) { null }
                }
            HostResourceFields.candidates(owners, name, kind, packageName,
                resources::getResourceTypeName, resources::getResourcePackageName)
        }
        val candidates = (listOf(direct) + reflected).filter { value ->
            value > 0 && runCatching { resources.getResourceTypeName(value) == kind &&
                resources.getResourcePackageName(value) == packageName }.getOrDefault(false)
        }.distinct()
        candidates.singleOrNull() ?: 0
    }
}
