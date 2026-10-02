package love.nairain.huawei.hook.util

import android.view.View
import android.os.Looper
import java.lang.ref.WeakReference

/** 队列与撤销闭包只持有 View 弱引用；脱离窗口或安装组失效后不再执行。 */
internal fun LayoutCallbackScope.post(view: View, apply: (View) -> Unit) {
    val target = WeakReference(view)
    var unregister: () -> Unit = {}
    val task = Runnable {
        unregister()
        run {
            target.get()?.takeIf { it.isAttachedToWindow }?.let(apply)
        }
    }
    unregister = onClose { target.get()?.removeCallbacks(task) }
    run {
        if (!view.post(task)) unregister()
    }
}

/** 生命周期和扫描完成回放共用相同的安全应用路径。 */
internal fun LayoutCallbackScope.applyPage(root: View?, apply: (View) -> Unit) {
    if (root == null) return
    if (Looper.myLooper() != Looper.getMainLooper()) {
        post(root) { applyPage(it, apply) }
        return
    }
    run {
        PageLayoutObserver.observe(root, this, apply)
        apply(root)
        post(root, apply)
    }
}
