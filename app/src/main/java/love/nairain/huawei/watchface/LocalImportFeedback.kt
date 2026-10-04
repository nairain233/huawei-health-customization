package love.nairain.huawei.watchface

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.lang.ref.WeakReference

/** 宿主窗口内显示原生反馈；不依赖网页配色，也不持有页面的强引用。 */
internal class LocalImportFeedback(activity: Activity) {
    private val activity = WeakReference(activity)
    private var dialog = WeakReference<AlertDialog>(null)
    private var label = WeakReference<TextView>(null)
    private var progress = WeakReference<ProgressBar>(null)

    fun progress(title: String, message: String, cancelLabel: String, percent: Int?, cancel: () -> Unit) {
        val owner = activity.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        if (dialog.get()?.isShowing != true || progress.get() == null) {
            dismiss()
            val context = themed(owner)
            val padding = (24 * context.resources.displayMetrics.density).toInt()
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
            }
            val status = TextView(context).apply { textSize = 16f }
            val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
            content.addView(status)
            content.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = padding / 2 })
            val next = AlertDialog.Builder(context).setTitle(title).setView(content)
                .setNegativeButton(cancelLabel) { _, _ -> cancel() }
                .setOnCancelListener { cancel() }.create()
            next.setCanceledOnTouchOutside(false)
            label = WeakReference(status)
            progress = WeakReference(bar)
            dialog = WeakReference(next)
            next.show()
        }
        label.get()?.text = message
        progress.get()?.apply { isIndeterminate = percent == null; if (percent != null) setProgress(percent.coerceIn(0, 100)) }
    }

    fun result(title: String, message: String, ok: String) {
        dismiss()
        val owner = activity.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        val next = AlertDialog.Builder(themed(owner)).setTitle(title).setMessage(message)
            .setPositiveButton(ok, null).create()
        dialog = WeakReference(next)
        next.show()
    }

    fun dismiss() {
        dialog.get()?.dismiss()
        dialog.clear(); label.clear(); progress.clear()
    }

    companion object {
        fun themed(activity: Activity): ContextThemeWrapper {
            val dark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return ContextThemeWrapper(activity, if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
        }
    }
}
