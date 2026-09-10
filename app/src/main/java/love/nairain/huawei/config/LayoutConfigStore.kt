package love.nairain.huawei.config

import android.annotation.SuppressLint
import android.content.SharedPreferences

internal enum class LayoutWriteOutcome {
    SUCCESS,
    FAILED_RESTORED,
    FAILED_UNCERTAIN,
}

internal data class LayoutLoadResult(
    val values: Map<String, Boolean>,
    val defaultWriteOutcome: LayoutWriteOutcome,
)

internal data class LayoutSaveResult(
    val values: Map<String, Boolean>,
    val outcome: LayoutWriteOutcome,
    val changed: Boolean,
)

/**
 * 布局配置的确认式存储层。RemotePreferences 提交失败时本地缓存可能已变化，
 * 因此每次写入都会保存相关键的存在状态和原值，并通过第二次 commit 精确恢复。
 * 所有方法只能由协调器的工作线程调用。
 */
@SuppressLint("ApplySharedPref", "UseKtx") // 需要 commit 返回值确认远端写入与第二次回滚。
internal class LayoutConfigStore {
    fun load(preferences: SharedPreferences): LayoutLoadResult {
        val original = preferences.snapshot()
        val values = SettingsCatalog.read(preferences)
        val defaults = SettingsCatalog.missingDefaults(original)
        return LayoutLoadResult(
            values = values,
            defaultWriteOutcome = commit(preferences, defaults, original),
        )
    }

    fun save(
        preferences: SharedPreferences,
        confirmedValues: Map<String, Boolean>,
        key: String,
        value: Boolean,
    ): LayoutSaveResult {
        val previous = SettingsCatalog.normalize(confirmedValues)
        if (!SettingsCatalog.defaults.containsKey(key) || previous[key] == value) {
            return LayoutSaveResult(previous, LayoutWriteOutcome.SUCCESS, changed = false)
        }

        val next = SettingsCatalog.normalizeWrite(key, value, previous)
        if (next == previous) {
            return LayoutSaveResult(previous, LayoutWriteOutcome.SUCCESS, changed = false)
        }
        val original = preferences.snapshot()
        val writes = LinkedHashMap(SettingsCatalog.missingDefaults(original))
        next.forEach { (settingKey, settingValue) ->
            if (original[settingKey] != settingValue) writes[settingKey] = settingValue
        }
        val outcome = commit(preferences, writes, original)
        return LayoutSaveResult(
            values = if (outcome == LayoutWriteOutcome.SUCCESS) next else previous,
            outcome = outcome,
            changed = true,
        )
    }

    private fun commit(
        preferences: SharedPreferences,
        writes: Map<String, Any>,
        original: Map<String, *>,
    ): LayoutWriteOutcome {
        if (writes.isEmpty()) return LayoutWriteOutcome.SUCCESS
        val saved = try {
            val editor = preferences.edit()
            writes.forEach { (key, value) -> editor.putValue(key, value) }
            editor.commit()
        } catch (_: RuntimeException) {
            false
        }
        if (saved) return LayoutWriteOutcome.SUCCESS
        return if (restore(preferences, writes.keys, original)) {
            LayoutWriteOutcome.FAILED_RESTORED
        } else {
            LayoutWriteOutcome.FAILED_UNCERTAIN
        }
    }

    private fun restore(
        preferences: SharedPreferences,
        keys: Set<String>,
        original: Map<String, *>,
    ): Boolean = try {
        val editor = preferences.edit()
        keys.forEach { key ->
            if (original.containsKey(key)) {
                editor.putValue(key, requireNotNull(original[key]))
            } else {
                editor.remove(key)
            }
        }
        editor.commit()
    } catch (_: RuntimeException) {
        false
    }

    private fun SharedPreferences.Editor.putValue(key: String, value: Any): SharedPreferences.Editor =
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is String -> putString(key, value)
            is Set<*> -> {
                val strings = value.mapTo(linkedSetOf()) { element ->
                    require(element is String) { "Unsupported preference set value" }
                    element
                }
                putStringSet(key, strings)
            }
            else -> error("Unsupported preference value type")
        }

    private fun SharedPreferences.snapshot(): Map<String, Any?> = all.mapValues { (_, value) ->
        if (value is Set<*>) value.toSet() else value
    }
}
