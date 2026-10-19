package love.nairain.huawei.hook.feature

import android.app.Activity
import android.view.View
import love.nairain.huawei.hook.util.LayoutCallbackScope
import love.nairain.huawei.hook.util.applyPage
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 页签可以复用已脱离窗口的根视图；重新附着时重新注册会在 detach 后关闭的布局观察器。 */
internal class HomeMorePageObserver(private val scope: LayoutCallbackScope, private val apply: (View, Activity) -> Unit) {
    private val roots = WeakHashMap<View, Listener>()
    init {
        scope.onClose {
            roots.entries.toList().forEach { (root, listener) -> root.removeOnAttachStateChangeListener(listener) }
            roots.clear()
        }
    }
    fun observe(root: View, activity: Activity) {
        if (!scope.isActive) return
        val listener = roots.getOrPut(root) {
            Listener(WeakReference(activity)).also(root::addOnAttachStateChangeListener)
        }
        listener.apply(root)
    }
    private inner class Listener(private val owner: WeakReference<Activity>) : View.OnAttachStateChangeListener {
        fun apply(root: View) {
            scope.applyPage(root) { page ->
                owner.get()?.takeUnless { it.isDestroyed || it.isFinishing }?.let { apply(page, it) }
            }
        }
        override fun onViewAttachedToWindow(view: View) = apply(view)
        override fun onViewDetachedFromWindow(view: View) = Unit
    }
}
