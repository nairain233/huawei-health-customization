package love.nairain.huawei.hook.feature

import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 只增加长按监听，不替换宿主的点击或触摸监听；脱离窗口后恢复原 longClickable。 */
internal class HomeMoreLongPress(private val failed: (Throwable) -> Unit = {}) : AutoCloseable {
    private val bindings = WeakHashMap<View, Binding>()
    private var closed = false

    fun bind(view: View, open: (View) -> Unit) {
        if (closed || bindings.containsKey(view) || view.isLongClickable) return
        val binding = Binding(view, open)
        bindings[view] = binding
        view.setOnLongClickListener(binding)
        view.addOnAttachStateChangeListener(binding)
    }

    private inner class Binding(view: View, private val open: (View) -> Unit) :
        View.OnLongClickListener, View.OnAttachStateChangeListener {
        private val target = WeakReference(view)
        override fun onLongClick(view: View): Boolean {
            if (!closed && bindings[view] === this && view.isShown && view.isEnabled) {
                runCatching { open(view) }.onFailure { error -> runCatching { failed(error) } }
            }
            return true // 即使打开失败也消费长按，防止松手触发宿主菜单。
        }
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = release()
        fun release() {
            target.get()?.let { view ->
                if (bindings[view] === this) {
                    bindings.remove(view)
                    view.setOnLongClickListener(null)
                    view.isLongClickable = false
                }
                view.removeOnAttachStateChangeListener(this)
            }
        }
    }
    override fun close() {
        if (closed) return
        closed = true
        bindings.values.toList().forEach { it.release() }
        bindings.clear()
    }
}
