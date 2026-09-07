package love.nairain.huawei.hook.resolver

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** AOSP Android 9–16 ContextImpl 签名白名单；厂商未知签名整项跳过。 */
internal data class ServiceHookTargets(
    val start: Method,
    val bind: Method,
    val unbind: Method,
    val outerContext: Method,
    val callers: List<Method>,
) {
    companion object {
        private const val INTENT = "android.content.Intent"
        private const val CONNECTION = "android.content.ServiceConnection"
        private const val USER = "android.os.UserHandle"
        private const val HANDLER = "android.os.Handler"
        private const val EXECUTOR = "java.util.concurrent.Executor"

        fun resolve(type: Class<*>): ServiceHookTargets {
            fun target(name: String, result: String, signatures: Set<List<String>>): Method {
                val candidates = type.declaredMethods.filter { it.name == name }
                require(candidates.size == 1) { "Missing or ambiguous service entry" }
                return candidates.single().also {
                    require(!Modifier.isStatic(it.modifiers) && it.returnType.name == result &&
                        it.parameterTypes.map(Class<*>::getName) in signatures) { "Unsupported service signature" }
                    it.isAccessible = true
                }
            }
            return ServiceHookTargets(
                target("startServiceCommon", "android.content.ComponentName", setOf(listOf(INTENT, "boolean", USER))),
                target("bindServiceCommon", "boolean", setOf(
                    listOf(INTENT, CONNECTION, "int", HANDLER, USER),
                    listOf(INTENT, CONNECTION, "int", "java.lang.String", HANDLER, EXECUTOR, USER),
                    listOf(INTENT, CONNECTION, "long", "java.lang.String", HANDLER, EXECUTOR, USER),
                )),
                target("unbindService", "void", setOf(listOf(CONNECTION))),
                target("getOuterContext", "android.content.Context", setOf(emptyList())),
                type.declaredMethods.filter {
                    it.name in setOf("startService", "startForegroundService", "startServiceAsUser",
                        "startForegroundServiceAsUser", "bindService", "bindIsolatedService", "bindServiceAsUser") &&
                        it.parameterTypes.firstOrNull()?.name == INTENT
                },
            )
        }
    }
}
