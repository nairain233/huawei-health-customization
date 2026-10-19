package love.nairain.huawei.config

import love.nairain.huawei.hook.HookInstallPolicy

/** 服务配置独立于布局 schema；只接受目标包的精确组件名。 */
internal data class ServiceBlockConfig(
    val enabled: Boolean = false,
    val components: Set<String> = emptySet(),
    val presets: Set<String> = emptySet(),
    val debugMode: Boolean = false,
) {
    fun blocks(packageName: String?, className: String?, declared: Set<String>): Boolean {
        if (!enabled) return false
        val component = componentName(packageName, className) ?: return false
        return component in components && component in declared
    }

    fun effectiveComponents(declared: Set<String>): Set<String> {
        if (!enabled) return emptySet()
        val presetComponents = presets.flatMap(ServicePreset::componentsFor)
            .filter { it in declared }
            .toSet()
        return presetComponents + if (debugMode) components.intersect(declared) else emptySet()
    }

    companion object {
        const val ENABLED = "service_block.enabled"
        const val COMPONENTS = "service_block.components"
        const val PRESETS = "service_block.presets"
        const val DEBUG_MODE = "service_block.debug_mode"

        fun componentName(packageName: String?, className: String?): String? {
            if (packageName != HookInstallPolicy.TARGET_PACKAGE || className.isNullOrBlank()) return null
            val fullName = when {
                className.startsWith('.') -> packageName + className
                '.' !in className -> "$packageName.$className"
                else -> className
            }
            if (fullName.split('.').any { part ->
                    part.isEmpty() || !Character.isJavaIdentifierStart(part[0]) ||
                        part.drop(1).any { !Character.isJavaIdentifierPart(it) }
                }) return null
            return "$packageName/$fullName"
        }

        fun normalize(value: String): String? {
            val parts = value.split('/')
            return if (parts.size == 2) componentName(parts[0], parts[1]) else null
        }

    }
}
