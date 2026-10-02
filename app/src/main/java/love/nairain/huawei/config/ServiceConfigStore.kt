package love.nairain.huawei.config

import android.content.SharedPreferences

internal data class ServiceSaveResult(val saved: Boolean, val restored: Boolean = true)

/** RemotePreferences.commit 会先更新本地缓存，失败时必须尝试恢复旧值。仅在工作线程调用。 */
internal object ServiceConfigStore {
    fun save(preferences: SharedPreferences, next: ServiceBlockConfig): ServiceSaveResult {
        val original = preferences.snapshot()
        val saved = try {
            ServiceBlockConfig.write(preferences, next)
        } catch (_: RuntimeException) {
            false
        }
        if (saved) return ServiceSaveResult(true)
        return ServiceSaveResult(false, restorePreferences(preferences, KEYS, original))
    }

    private val KEYS = setOf(ServiceBlockConfig.ENABLED, ServiceBlockConfig.COMPONENTS,
        ServiceBlockConfig.PRESETS, ServiceBlockConfig.DEBUG_MODE)
}
