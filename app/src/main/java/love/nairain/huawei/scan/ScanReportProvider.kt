package love.nairain.huawei.scan

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

/** 仅接收目标 UID 的脱敏报告；不暴露配置写入、文件或查询接口。 */
class ScanReportProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = requireNotNull(context)
        val uid = Binder.getCallingUid()
        require(ReportAdmission.allows(uid, Process.myUid(), ctx.packageManager.getPackagesForUid(uid)
            .orEmpty().toList())) { "Caller not allowed" }
        require(method == "report" && extras == null)
        val accepted = ScanReportStore.accept(ctx, requireNotNull(arg))
        return Bundle().apply { putBoolean("accepted", accepted) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
