package love.nairain.huawei.hook.feature

import love.nairain.huawei.config.SettingsKeys
import java.util.Collections
import java.util.WeakHashMap

class BottomTabStateStore<K : Any> {
    private val hidden = Collections.synchronizedMap(WeakHashMap<K, MutableSet<Int>>())

    fun record(instance: K, index: Int, isHidden: Boolean) {
        val indexes = hidden.getOrPut(instance) { mutableSetOf() }
        if (isHidden) indexes += index else indexes -= index
    }

    fun clear(instance: K) {
        hidden.remove(instance)
    }

    fun snapshot(instance: K): Set<Int> = hidden[instance]?.toSet().orEmpty()
}

object BottomLayoutOrder {
    fun <T> arrange(items: List<T>, rtl: Boolean): List<T> = if (rtl) items.reversed() else items
}

object BottomTabIndexResolver {
    private val keys = listOf(
        SettingsKeys.BOTTOM_HEALTH,
        SettingsKeys.BOTTOM_SPORT,
        SettingsKeys.BOTTOM_MEMBER,
        SettingsKeys.BOTTOM_DEVICE,
        SettingsKeys.BOTTOM_MINE,
    )

    /** 基准版 MainActivity 会按模式省略会员、运动或设备；只有完整五项可使用固定索引。 */
    fun resolve(index: Int, itemCount: Int = keys.size): String? =
        if (itemCount == keys.size) keys.getOrNull(index) else null
}
