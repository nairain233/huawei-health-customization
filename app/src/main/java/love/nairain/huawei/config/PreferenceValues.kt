package love.nairain.huawei.config

import android.annotation.SuppressLint
import android.content.SharedPreferences

/** 保存原始类型和集合副本；失败回滚只触及本次写入的键。 */
internal fun SharedPreferences.snapshot(): Map<String, Any?> = all.mapValues { (_, value) ->
    if (value is Set<*>) value.toSet() else value
}

@SuppressLint("ApplySharedPref", "UseKtx") // 回滚也必须确认远端提交。
internal fun restorePreferences(
    preferences: SharedPreferences,
    keys: Set<String>,
    original: Map<String, *>,
): Boolean = try {
    val editor = preferences.edit()
    keys.forEach { key ->
        if (original.containsKey(key)) editor.putValue(key, requireNotNull(original[key]))
        else editor.remove(key)
    }
    editor.commit()
} catch (_: RuntimeException) {
    false
}

internal fun SharedPreferences.Editor.putValue(key: String, value: Any): SharedPreferences.Editor =
    when (value) {
        is Boolean -> putBoolean(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is String -> putString(key, value)
        is Set<*> -> putStringSet(key, value.mapTo(linkedSetOf()) { element ->
            require(element is String) { "Unsupported preference set value" }
            element
        })
        else -> error("Unsupported preference value type")
    }
