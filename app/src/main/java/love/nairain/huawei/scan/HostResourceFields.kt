package love.nairain.huawei.scan

import java.lang.reflect.Modifier

/** R 字段不必为 final；字段可见性、类型和当前资源身份仍须同时通过校验。 */
internal object HostResourceFields {
    fun resolve(
        owners: List<Class<*>>,
        name: String,
        kind: String,
        packageName: String,
        resourceType: (Int) -> String,
        resourcePackage: (Int) -> String,
    ): Int = owners.mapNotNull { owner ->
        try {
            val field = owner.getDeclaredField(name)
            if (field.type != Int::class.javaPrimitiveType || !Modifier.isStatic(field.modifiers) ||
                !Modifier.isPublic(field.modifiers)) null
            else field.getInt(null).takeIf {
                it != 0 && resourceType(it) == kind && resourcePackage(it) == packageName
            }
        } catch (_: ReflectiveOperationException) { null }
        catch (_: RuntimeException) { null } // 缺失资源、权限受限时保持未命中。
        catch (_: LinkageError) { null }
    }.distinct().singleOrNull() ?: 0
}
