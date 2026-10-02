package love.nairain.huawei.hook.resolver

import java.util.Collections
import java.util.IdentityHashMap

/** 已核验宿主使用 AndroidX Fragment；回放只访问目标实例，不按整个 Activity 的共享 ID 猜测页面。 */
internal object ActiveFragmentTargets {
    fun forEach(activity: Any, type: Class<*>, apply: (Any) -> Unit) {
        val entry = try {
            activity.javaClass.getMethod("getSupportFragmentManager")
        } catch (_: NoSuchMethodException) {
            return // 普通 Activity 没有 FragmentManager，不属于页面目标。
        }
        val first = entry.invoke(activity) ?: return
        val managers = ArrayDeque<Any>().apply { add(first) }
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        while (managers.isNotEmpty()) {
            val manager = managers.removeFirst()
            if (!visited.add(manager)) continue
            val fragments = manager.javaClass.getMethod("getFragments").invoke(manager) as? List<*> ?: continue
            fragments.filterNotNull().forEach { fragment ->
                if (fragment.javaClass.getMethod("isAdded").invoke(fragment) != true) return@forEach
                if (type.isInstance(fragment)) apply(fragment)
                fragment.javaClass.getMethod("getChildFragmentManager").invoke(fragment)?.let(managers::add)
            }
        }
    }
}
