package love.nairain.huawei.hook.util

import android.view.View
import android.view.ViewTreeObserver
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 监听页面内异步插入的视图，合并同根视图、同安装组的待执行回调。 */
object PageLayoutObserver {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = WeakHashMap<View, MutableMap<LayoutCallbackScope, Listener>>()

    fun observe(root: View, scope: LayoutCallbackScope, apply: (View) -> Unit) {
        if (!scope.isActive) return
        synchronized(listeners) {
            val sources = listeners.getOrPut(root) { mutableMapOf() }
            if (sources.containsKey(scope)) return
            val listener = Listener(WeakReference(root), scope, apply)
            sources[scope] = listener
            try {
                root.viewTreeObserver.addOnGlobalLayoutListener(listener)
                root.addOnAttachStateChangeListener(listener)
                listener.unregister = scope.onClose {
                    val cleanup = { scope.cleanup { listener.close() } }
                    if (Looper.myLooper() == Looper.getMainLooper()) cleanup() else main.post(cleanup)
                }
            } catch (error: Throwable) {
                scope.cleanup { listener.close() }
                throw error
            }
        }
    }

    private class Listener(
        private val root: WeakReference<View>,
        private val scope: LayoutCallbackScope,
        private val apply: (View) -> Unit,
    ) : ViewTreeObserver.OnGlobalLayoutListener, View.OnAttachStateChangeListener {
        private var pending = false
        private var closed = false
        var unregister: () -> Unit = {}
        private val task = Runnable {
            pending = false
            scope.run {
                if (!closed) root.get()?.takeIf { it.isAttachedToWindow }?.let(apply)
            }
        }

        override fun onGlobalLayout() {
            val view = root.get() ?: return
            if (closed || !scope.isActive || pending) return
            pending = true
            scope.run { if (!view.post(task)) pending = false }
        }

        override fun onViewAttachedToWindow(view: View) = Unit

        override fun onViewDetachedFromWindow(view: View) {
            scope.cleanup { close() }
        }

        fun close() {
            if (closed) return
            closed = true
            pending = false
            val view = root.get()
            try {
                if (view != null) {
                    view.removeCallbacks(task)
                    if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    view.removeOnAttachStateChangeListener(this)
                }
            } finally {
                synchronized(listeners) {
                    listeners[view]?.let { sources ->
                        sources.remove(scope)
                        if (sources.isEmpty()) listeners.remove(view)
                    }
                }
                unregister()
            }
        }
    }
}
