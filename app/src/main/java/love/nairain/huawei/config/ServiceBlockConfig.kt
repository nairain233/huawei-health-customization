package love.nairain.huawei.config

import android.content.SharedPreferences
import android.annotation.SuppressLint
import love.nairain.huawei.hook.HookInstallPolicy

/** 服务配置独立于布局 schema；只接受目标包的精确组件名。 */
internal data class ServiceBlockConfig(
    val enabled: Boolean = false,
    val components: Set<String> = emptySet(),
) {
    fun blocks(packageName: String?, className: String?, declared: Set<String>): Boolean {
        if (!enabled) return false
        val component = componentName(packageName, className) ?: return false
        return component in components && component in declared
    }

    companion object {
        const val ENABLED = "service_block.enabled"
        const val COMPONENTS = "service_block.components"

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

        /** 类型损坏交给调用者报告；Hook 调用者必须降级为全部放行。 */
        fun read(preferences: SharedPreferences): ServiceBlockConfig {
            val values = preferences.all
            val enabled = values[ENABLED] ?: false
            val components = values[COMPONENTS] ?: emptySet<String>()
            require(enabled is Boolean && components is Set<*> && components.all { it is String }) {
                "Invalid service configuration types"
            }
            return ServiceBlockConfig(enabled, components.mapNotNull { normalize(it as String) }.toSet())
        }

        @SuppressLint("UseKtx") // KTX edit 返回 Unit；此处必须检查 commit 的持久化结果。
        fun write(preferences: SharedPreferences, config: ServiceBlockConfig): Boolean =
            preferences.edit()
                .putBoolean(ENABLED, config.enabled)
                .putStringSet(COMPONENTS, config.components.mapNotNull(::normalize).toSet())
                .commit()
    }
}
