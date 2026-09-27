package love.nairain.huawei.hook.util

import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 监听页面内异步插入的视图；同一根视图每帧最多处理一次。 */
object PageLayoutObserver {
    private val listeners = WeakHashMap<View, Listener>()

    fun observe(root: View, apply: (View) -> Unit) {
        synchronized(listeners) {
            if (listeners.containsKey(root)) return
            val listener = Listener(WeakReference(root), apply)
            listeners[root] = listener
            root.viewTreeObserver.addOnGlobalLayoutListener(listener)
            root.addOnAttachStateChangeListener(listener)
        }
    }

    private class Listener(
        private val root: WeakReference<View>,
        private val apply: (View) -> Unit,
    ) : ViewTreeObserver.OnGlobalLayoutListener, View.OnAttachStateChangeListener {
        private var pending = false

        override fun onGlobalLayout() {
            val view = root.get() ?: return
            if (pending) return
            pending = true
            view.post {
                pending = false
                if (view.isAttachedToWindow) apply(view)
            }
        }

        override fun onViewAttachedToWindow(view: View) = Unit

        override fun onViewDetachedFromWindow(view: View) {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(this)
            view.removeOnAttachStateChangeListener(this)
            synchronized(listeners) { listeners.remove(view) }
        }
    }
}
