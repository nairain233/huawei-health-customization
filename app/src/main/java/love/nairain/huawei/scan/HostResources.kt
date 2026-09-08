package love.nairain.huawei.scan

import android.annotation.SuppressLint
import android.content.res.Resources
import java.lang.reflect.Modifier

/** 资源名被压缩时只读取当前 APK 的 R 常量，不复用历史版本的数字 ID。 */
internal class HostResources(private val resources: Resources, private val loader: ClassLoader, private val packageName: String) {
    private val cache = mutableMapOf<String, Int>()

    @SuppressLint("DiscouragedApi")
    fun id(name: String, kind: String): Int = cache.getOrPut("$kind/$name") {
        val direct = resources.getIdentifier(name, kind, packageName)
        if (direct != 0) direct else {
            listOf("com.huawei.ui.main.R", "com.huawei.ui.homehealth.R", "com.huawei.health.R")
                .mapNotNull { owner ->
                    try {
                        val field = Class.forName("$owner\$$kind", false, loader).getDeclaredField(name)
                        if (field.type != Int::class.javaPrimitiveType || !Modifier.isStatic(field.modifiers) || !Modifier.isFinal(field.modifiers)) null
                        else field.getInt(null).takeIf { it != 0 && resources.getResourceTypeName(it) == kind && resources.getResourcePackageName(it) == packageName }
                    } catch (_: ReflectiveOperationException) { null }
                    catch (_: Resources.NotFoundException) { null }
                }.distinct().singleOrNull() ?: 0
        }
    }
}
