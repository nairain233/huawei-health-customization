package love.nairain.huawei.hook.feature

import android.os.Looper
import android.view.View
import love.nairain.huawei.config.*
import love.nairain.huawei.hook.HookContext
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 页面身份与导航弱状态；不再生成或持久化内容目录。主线程访问。 */
internal class SportPageRuntime {
    private val fragments = WeakHashMap<Any, Int>()
    private val roots = WeakHashMap<View, Int>()
    private val triggers = WeakHashMap<Any, Int>()
    private val sectionBeans = WeakHashMap<Any, List<WeakReference<Any>>>()
    private var tabs = emptyList<Int>()
    private val beans = WeakHashMap<Any, List<WeakReference<Any>>>()
    private val ownerTabs = WeakHashMap<Any, List<Int>>()
    var navigationActive = false

    fun id(bean: Any, context: HookContext): Int? = context.targets.invokeNoArgs(bean, "sport.tab.id") as? Int

    fun tabs(source: List<Any>, owner: Any, context: HookContext): List<Any> {
        if (Looper.myLooper() != Looper.getMainLooper()) return source
        val records = source.map { bean ->
            val id = id(bean, context) ?: return source
            val fragment = context.targets.invokeNoArgs(bean, "sport.tab.fragment")
            fragment to id
        }
        if (records.map { it.second }.distinct().size != records.size) return source
        records.forEach { (fragment, id) -> if (fragment != null) fragments[fragment] = id }
        beans[owner] = source.map(::WeakReference)
        val next = records.map { it.second }
        synchronized(ownerTabs) { ownerTabs[owner] = next }
        if (next != tabs) { tabs = next; context.sportTabsChanged(tabs) }
        bindRoots(context)
        return if (navigationActive) SportTabPolicy.visible(source, { id(it, context) }, context.sport, context.apkIdentity) else source
    }

    fun available(owner: Any, context: HookContext): List<Int> =
        SportTabPolicy.visible(synchronized(ownerTabs) { ownerTabs[owner].orEmpty() }, { it }, context.sport, context.apkIdentity)

    fun changed(bean: Any, remove: Boolean, owner: Any, context: HookContext): List<Any>? {
        val id = id(bean, context) ?: return null
        val full = beans[owner]?.map { it.get() ?: return null }?.filter { id(it, context) != id }?.toMutableList() ?: return null
        if (!remove) full += bean
        return full.takeIf { it.isNotEmpty() }
    }

    fun ordered(source: List<Any>, owner: Any, context: HookContext): List<Any> {
        if (Looper.myLooper() != Looper.getMainLooper()) return source
        val ids = source.map { id(it, context) }
        if (ids.any { it == null } || ids.distinct().size != ids.size) return source
        val hidden = beans[owner].orEmpty().mapNotNull { it.get() }.filter { bean ->
            val id = id(bean, context)
            id != null && id !in ids && context.sport.hidesTab(context.apkIdentity, id)
        }
        return tabs(source + hidden, owner, context)
    }

    fun restoreNavigation(context: HookContext) {
        navigationActive = false
        replayNavigation(context)
    }

    fun replayNavigation(context: HookContext) {
        val method = context.targets.methodForRole("sport.tabs") ?: return
        beans.entries.toList().forEach { (owner, refs) ->
            val values = refs.mapNotNull { it.get() }
            if (values.size == refs.size && values.isNotEmpty()) method.invoke(owner, ArrayList(values))
        }
    }

    fun bindRoots(context: HookContext) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        beans.values.toList().flatten().mapNotNull { it.get() }.forEach { bean ->
            val id = id(bean, context) ?: return@forEach
            context.targets.invokeNoArgs(bean, "sport.tab.fragment")?.let { fragments[it] = id }
        }
        fragments.entries.toList().forEach { (fragment, page) ->
            (context.targets.invokeNoArgs(fragment, "getView") as? View)?.let { roots[it] = page }
            context.targets.invokeNoArgs(fragment, "getPageResTrigger")?.let { triggers[it] = page }
        }
    }

    fun pageCreated(fragment: Any, root: View?, context: HookContext): Int? {
        val page = fragments[fragment] ?: return null
        root?.let { roots[it] = page }
        context.targets.invokeNoArgs(fragment, "getPageResTrigger")?.let { triggers[it] = page }
        return page
    }

    fun rootsUnder(parent: View, context: HookContext): List<Pair<View, Int>> {
        bindRoots(context)
        return roots.entries.toList().filter { (root, _) ->
            generateSequence(root) { it.parent as? View }.any { it === parent }
        }.map { it.key to it.value }
    }

    fun pageOf(view: View?, context: HookContext): Int? {
        bindRoots(context)
        return generateSequence(view) { it.parent as? View }.firstNotNullOfOrNull { roots[it] }
    }

    fun sectionList(trigger: Any, values: List<*>, context: HookContext): List<Pair<View, Int>> {
        bindRoots(context)
        sectionBeans[trigger] = values.filterNotNull().map(::WeakReference)
        val page = triggers[trigger] ?: return emptyList()
        return roots.entries.filter { it.value == page }.map { it.key to page }
    }
    fun rootsUnderPage(page: Int): List<View> = roots.entries.filter { it.value == page }.map { it.key }
    fun sections(page: Int, context: HookContext): List<View> = sectionBeans.entries
        .filter { triggers[it.key] == page }.flatMap { it.value }.mapNotNull { ref ->
            ref.get()?.let { context.targets.invokeNoArgs(it, "section.view") as? View }
        }.filter { pageOf(it, context) == page }.distinct()
}
