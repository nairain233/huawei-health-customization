package love.nairain.huawei.hook.util

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.view.isGone
import java.util.WeakHashMap

/** 只隐藏角标可见性；保留尺寸、点击与数据，绑定前恢复以记录宿主最新显示结果。 */
internal class BadgeViews(
    private val scope: LayoutCallbackScope,
    private val selectors: ViewSelectors,
    private val names: Set<String>,
) {
    private val originals = WeakHashMap<View, Int>()

    init {
        scope.onClose {
            val restore = {
                originals.entries.toList().forEach { (view, visibility) ->
                    if (view.isGone) view.visibility = visibility
                }
                originals.clear()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) restore()
            else Handler(Looper.getMainLooper()).post { scope.cleanup(restore) }
        }
    }

    fun beforeUpdate(root: View?) {
        if (!scope.isActive || root == null || Looper.myLooper() != Looper.getMainLooper()) return
        selectors.walk(root) { view ->
            originals.remove(view)?.let { visibility ->
                if (view.isGone) view.visibility = visibility
            }
        }
    }

    fun hide(root: View?) {
        if (!scope.isActive || root == null) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.post(root, ::hide)
            return
        }
        selectors.walk(root) { view ->
            if (selectors.resourceEntryName(view) in names) {
                if (!originals.containsKey(view) || view.visibility != View.GONE) originals[view] = view.visibility
                // 每次重新断言 GONE，不能因已有快照而忽略宿主再次显示。
                if (view.visibility != View.GONE) view.visibility = View.GONE
            }
        }
    }
}
