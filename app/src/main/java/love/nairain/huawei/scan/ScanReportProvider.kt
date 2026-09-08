package love.nairain.huawei.scan

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.annotation.SuppressLint
import androidx.core.net.toUri

/** 仅接收目标 UID 的脱敏报告；不暴露配置写入、文件或查询接口。 */
class ScanReportProvider : ContentProvider() {
    override fun onCreate() = true

    @Synchronized
    @SuppressLint("UseKtx") // 必须检查 commit 返回值，失败不能确认报告已保存。
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = requireNotNull(context)
        val uid = Binder.getCallingUid()
        require(ReportAdmission.allows(uid, Process.myUid(), ctx.packageManager.getPackagesForUid(uid)
            .orEmpty().toList())) { "Caller not allowed" }
        require(method == "report" && extras == null)
        val report = ScanReport.decode(requireNotNull(arg))
        require(report.rules == ScanProtocol.RULES)
        require(report.time <= System.currentTimeMillis() + 30_000)
        val prefs = ctx.getSharedPreferences(ScanProtocol.STORE, 0)
        val previous = prefs.getString("report", null)?.let { runCatching { ScanReport.decode(it) }.getOrNull() }
        val accepted = report.follows(previous)
        if (accepted) {
            check(prefs.edit().putString("report", report.encode()).commit())
            ctx.contentResolver.notifyChange("content://${ScanProtocol.AUTHORITY}".toUri(), null)
        }
        return Bundle().apply { putBoolean("accepted", accepted) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
