package love.nairain.huawei.hook.util

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import java.util.WeakHashMap

/**
 * 通用视图折叠工具：隐藏视图、把占用空间归零，并在需要时恢复原始状态。
 *
 * 快照使用 [WeakHashMap] 保存，只持有布局参数和布尔状态，不持有 Activity、Fragment
 * 或 View 的强引用，避免影响目标应用页面生命周期。
 */
object ViewTrimmer {
    private val snapshots = WeakHashMap<View, ViewSnapshot>()

    fun collapse(view: View) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            view.post { collapse(view) }
            return
        }
        if (isCollapsed(view)) return

        val layoutParams = view.layoutParams
        snapshots[view] = ViewSnapshot(
            visibility = view.visibility,
            layout = LayoutSnapshot.from(layoutParams),
        )

        view.visibility = View.GONE
        layoutParams?.apply {
            width = 0
            height = 0
            if (this is ViewGroup.MarginLayoutParams) {
                setMargins(0, 0, 0, 0)
                if (isMarginRelative) {
                    marginStart = 0
                    marginEnd = 0
                }
            }
        }
        view.layoutParams = layoutParams
        view.layout(0, 0, 0, 0)
    }

    fun restore(view: View) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            view.post { restore(view) }
            return
        }
        val snapshot = snapshots.remove(view) ?: return

        snapshot.layout?.applyTo(view)
        view.visibility = snapshot.visibility
        view.requestLayout()
    }

    fun isCollapsed(view: View): Boolean = snapshots.containsKey(view)

    private data class LayoutSnapshot(
        val width: Int,
        val height: Int,
        val marginLeft: Int,
        val marginTop: Int,
        val marginRight: Int,
        val marginBottom: Int,
        val marginStart: Int,
        val marginEnd: Int,
        val wasMarginRelative: Boolean,
        val wasMarginLayoutParams: Boolean,
    ) {
        fun applyTo(view: View) {
            val current = view.layoutParams
            if (current != null) {
                current.width = width
                current.height = height
                if (wasMarginLayoutParams && current is ViewGroup.MarginLayoutParams) {
                    current.setMargins(marginLeft, marginTop, marginRight, marginBottom)
                    if (wasMarginRelative) {
                        current.marginStart = marginStart
                        current.marginEnd = marginEnd
                    }
                }
                view.layoutParams = current
                return
            }

            val created = if (wasMarginLayoutParams) {
                ViewGroup.MarginLayoutParams(width, height).apply {
                    setMargins(marginLeft, marginTop, marginRight, marginBottom)
                    if (wasMarginRelative) {
                        marginStart = this@LayoutSnapshot.marginStart
                        marginEnd = this@LayoutSnapshot.marginEnd
                    }
                }
            } else {
                ViewGroup.LayoutParams(width, height)
            }
            view.layoutParams = created
        }

        companion object {
            fun from(layoutParams: ViewGroup.LayoutParams?): LayoutSnapshot? {
                if (layoutParams == null) return null
                return LayoutSnapshot(
                    width = layoutParams.width,
                    height = layoutParams.height,
                    marginLeft = (layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0,
                    marginTop = (layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0,
                    marginRight = (layoutParams as? ViewGroup.MarginLayoutParams)?.rightMargin ?: 0,
                    marginBottom = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0,
                    marginStart = (layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart ?: 0,
                    marginEnd = (layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd ?: 0,
                    wasMarginRelative = (layoutParams as? ViewGroup.MarginLayoutParams)
                        ?.isMarginRelative == true,
                    wasMarginLayoutParams = layoutParams is ViewGroup.MarginLayoutParams,
                )
            }
        }
    }

    private data class ViewSnapshot(
        val visibility: Int,
        val layout: LayoutSnapshot?,
    )
}
