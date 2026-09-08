package love.nairain.huawei.hook.resolver

import java.lang.reflect.Executable
import java.lang.reflect.Method
import org.luckypray.dexkit.wrap.DexMethod

internal object ReflectionTargets {
    @Volatile
    var aliases: Map<String, String> = emptyMap()
    @Volatile
    var resolvedDescriptors: Set<String>? = null
    private fun allowed(method: Method) = resolvedDescriptors?.contains(DexMethod(method).toString()) != false
    private fun mapped(type: Class<*>, name: String, count: Int): String =
        generateSequence(type as Class<*>?) { it.superclass }.firstNotNullOfOrNull {
            aliases["${it.name}#$name#$count"]
        } ?: name
    fun type(classLoader: ClassLoader, name: String): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    fun method(
        type: Class<*>,
        name: String,
        parameterCount: Int,
        returnType: Class<*>? = null,
    ): Method? = allMethods(type).firstOrNull {
        it.name == mapped(type, name, parameterCount) && it.parameterCount == parameterCount &&
            (returnType == null || it.returnType == returnType) && allowed(it)
    }?.apply { isAccessible = true }

    fun methods(type: Class<*>, name: String, parameterCount: Int): List<Method> =
        allMethods(type).filter { it.name == mapped(type, name, parameterCount) && it.parameterCount == parameterCount && allowed(it) }
            .onEach { it.isAccessible = true }
            .toList()

    fun constructor(type: Class<*>, parameterCount: Int): Executable? =
        type.declaredConstructors.singleOrNull { it.parameterCount == parameterCount &&
            resolvedDescriptors?.contains(DexMethod(it).toString()) != false }
            ?.apply { isAccessible = true }

    fun invokeNoArgs(target: Any, name: String): Any? = runCatching {
        allMethods(target.javaClass).firstOrNull { it.name == mapped(target.javaClass, name, 0) && it.parameterCount == 0 }
            ?.apply { isAccessible = true }
            ?.invoke(target)
    }.getOrNull()

    fun fieldValue(target: Any, name: String): Any? = runCatching {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            current.declaredFields.firstOrNull { it.name == (aliases["${current.name}#$name#field"] ?: name) }?.let { field ->
                field.isAccessible = true
                return@runCatching field.get(target)
            }
            current = current.superclass
        }
        null
    }.getOrNull()

    private fun allMethods(type: Class<*>): Sequence<Method> = sequence {
        var current: Class<*>? = type
        while (current != null) {
            yieldAll(current.declaredMethods.asSequence())
            current = current.superclass
        }
    }
}
