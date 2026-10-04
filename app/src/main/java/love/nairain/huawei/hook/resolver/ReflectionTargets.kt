package love.nairain.huawei.hook.resolver

import java.lang.reflect.Executable
import java.lang.reflect.Method
import org.luckypray.dexkit.wrap.DexMethod
import org.luckypray.dexkit.wrap.DexField
import love.nairain.huawei.scan.LayoutResolution

/** 按完整描述符解析；没有扫描证据时不回退到同名或同参数数量成员。 */
internal class ReflectionTargets(private val resolution: LayoutResolution, private val loader: ClassLoader) {
    val identities get() = resolution.identities

    private fun binding(type: Class<*>, role: String, suffix: String): String? =
        generateSequence(type as Class<*>?) { it.superclass }.firstNotNullOfOrNull {
            resolution.bindings["${it.name}#$role#$suffix"]
        }

    fun type(classLoader: ClassLoader, name: String): Class<*>? =
        if ("L${name.replace('.', '/')};" !in resolution.descriptors) null
        else runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    fun method(type: Class<*>, role: String, parameterCount: Int, returnType: Class<*>? = null): Method? =
        binding(type, role, parameterCount.toString())?.let { descriptor ->
            runCatching { DexMethod(descriptor).getMethodInstance(loader).apply { isAccessible = true } }
                .getOrNull()?.takeIf { it.declaringClass.isAssignableFrom(type) &&
                    it.parameterCount == parameterCount && (returnType == null || it.returnType == returnType) }
        }

    fun methods(type: Class<*>, role: String, parameterCount: Int): List<Method> =
        listOfNotNull(method(type, role, parameterCount))

    fun constructor(type: Class<*>, parameterCount: Int): Executable? =
        binding(type, "<init>", parameterCount.toString())?.let { descriptor ->
            runCatching { DexMethod(descriptor).getConstructorInstance(loader).apply { isAccessible = true } }.getOrNull()
        }

    fun invokeNoArgs(target: Any, role: String): Any? = runCatching {
        method(target.javaClass, role, 0)?.invoke(target)
    }.getOrNull()

    fun fieldValue(target: Any, role: String): Any? = binding(target.javaClass, role, "field")?.let { descriptor ->
        runCatching { DexField(descriptor).getFieldInstance(loader)
            .apply { isAccessible = true }.get(target) }.getOrNull()
    }

    fun marketing(classLoader: ClassLoader): Method? = resolution.bindings["mine.marketing"]?.let {
        runCatching { DexMethod(it).getMethodInstance(classLoader).apply { isAccessible = true } }.getOrNull()
    }
}
