package love.nairain.huawei.scan

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.net.toUri

/** Provider、socket 与 UI 在同一进程内共享已确认快照，避免交叉覆盖更新。 */
internal object ScanReportStore {
    private var inbox: ScanReportInbox? = null

    @Synchronized
    @SuppressLint("UseKtx") // 必须检查持久化结果，不能提前确认报告。
    private fun inbox(context: Context): ScanReportInbox = inbox ?: run {
        val prefs = context.applicationContext.getSharedPreferences(ScanProtocol.STORE, 0)
        ScanReportInbox({ prefs.getString("report", null) }, { prefs.edit().putString("report", it).commit() })
            .also { inbox = it }
    }

    fun read(context: Context): String? = inbox(context).snapshot()

    fun accept(context: Context, raw: String): Boolean {
        val accepted = inbox(context).accept(raw, System.currentTimeMillis())
        if (accepted) context.contentResolver.notifyChange("content://${ScanProtocol.AUTHORITY}".toUri(), null)
        return accepted
    }
}
