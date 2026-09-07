package love.nairain.huawei.config

import android.content.SharedPreferences

internal data class ServiceSaveResult(val saved: Boolean, val restored: Boolean = true)

/** RemotePreferences.commit 会先更新本地缓存，失败时必须尝试恢复旧值。仅在工作线程调用。 */
internal object ServiceConfigStore {
    fun save(preferences: SharedPreferences, previous: ServiceBlockConfig, next: ServiceBlockConfig): ServiceSaveResult {
        return try {
            if (ServiceBlockConfig.write(preferences, next)) ServiceSaveResult(true)
            else restore(preferences, previous)
        } catch (_: RuntimeException) {
            restore(preferences, previous)
        }
    }

    private fun restore(preferences: SharedPreferences, previous: ServiceBlockConfig): ServiceSaveResult {
        val restored = try {
            ServiceBlockConfig.write(preferences, previous)
        } catch (_: RuntimeException) {
            false
        }
        return ServiceSaveResult(false, restored)
    }
}
