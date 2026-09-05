package love.nairain.huawei.hook.resolver

import java.lang.reflect.Executable
import java.lang.reflect.Method

internal object ReflectionTargets {
    fun type(classLoader: ClassLoader, name: String): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    fun method(
        type: Class<*>,
        name: String,
        parameterCount: Int,
        returnType: Class<*>? = null,
    ): Method? = allMethods(type).firstOrNull {
        it.name == name && it.parameterCount == parameterCount &&
            (returnType == null || it.returnType == returnType)
    }?.apply { isAccessible = true }

    fun methods(type: Class<*>, name: String, parameterCount: Int): List<Method> =
        allMethods(type).filter { it.name == name && it.parameterCount == parameterCount }
            .onEach { it.isAccessible = true }
            .toList()

    fun constructor(type: Class<*>, parameterCount: Int): Executable? =
        type.declaredConstructors.firstOrNull { it.parameterCount == parameterCount }
            ?.apply { isAccessible = true }

    fun invokeNoArgs(target: Any, name: String): Any? = runCatching {
        allMethods(target.javaClass).firstOrNull { it.name == name && it.parameterCount == 0 }
            ?.apply { isAccessible = true }
            ?.invoke(target)
    }.getOrNull()

    fun fieldValue(target: Any, name: String): Any? = runCatching {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            current.declaredFields.firstOrNull { it.name == name }?.let { field ->
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
