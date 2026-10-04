package love.nairain.huawei.hook.util

/** 扫描快照同时提供正向 ID 与压缩资源名的反向身份；冲突身份不参与标题定位。 */
class ResourceIdResolver(private val resourceIds: Map<String, Int>) {
    private val namesById = resourceIds.filterKeys { it.startsWith("id/") }.entries.groupBy { it.value }
        .mapValues { (_, entries) -> entries.map { it.key.substringAfter('/') }.distinct().singleOrNull() }

    fun id(name: String, type: String = "id"): Int = resourceIds["$type/$name"] ?: 0
    fun name(id: Int): String? = namesById[id]
}
