package love.nairain.huawei.hook.util

import android.annotation.SuppressLint
import android.content.res.Resources
import java.util.concurrent.ConcurrentHashMap

/**
 * 动态资源 ID 查询，缓存正数和 0，避免重复调用 Resources.getIdentifier。
 */
object ResourceIdResolver {
    private val cache = ConcurrentHashMap<String, Int>()

    @SuppressLint("DiscouragedApi") // 目标应用资源无法通过模块自身的 R 类静态引用。
    fun id(
        resources: Resources,
        packageName: String,
        name: String,
        type: String = "id",
    ): Int {
        if (name.isBlank() || packageName.isBlank()) return 0
        val key = "$packageName:$type/$name"
        cache[key]?.let { return it }

        val value = try {
            resources.getIdentifier(name, type, packageName)
        } catch (_: RuntimeException) {
            0
        }
        cache[key] = value
        return value
    }
}
