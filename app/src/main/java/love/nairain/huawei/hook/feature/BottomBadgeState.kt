package love.nairain.huawei.hook.feature

import java.util.WeakHashMap

/** 保存每个角标最新的宿主请求；回放产生的 false 不能覆盖待恢复的 true。 */
internal class BottomBadgeState<T : Any>(private val targetClassName: String) {
    private val requested = WeakHashMap<T, Boolean>()

    fun update(ownerClassName: String, item: T, visible: Boolean): Boolean {
        if (ownerClassName != targetClassName) return visible
        requested[item] = visible
        return false
    }

    fun replay(ownerClassName: String, item: T, visible: Boolean): Boolean {
        if (ownerClassName != targetClassName) return visible
        if (!requested.containsKey(item) || visible) requested[item] = visible
        return false
    }

    fun snapshot(): Map<T, Boolean> = requested.toMap()
    fun clear() = requested.clear()
}
