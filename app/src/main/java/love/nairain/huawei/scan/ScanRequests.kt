package love.nairain.huawei.scan

import android.content.SharedPreferences
import android.annotation.SuppressLint

/** RemotePreferences 可能先修改本地缓存；失败时恢复旧值，不能乐观显示已预约。 */
internal object ScanRequests {
    @SuppressLint("ApplySharedPref", "UseKtx") // 工作线程需要 commit 返回值确认预约与回滚是否落盘。
    fun save(preferences: SharedPreferences, request: String): Boolean {
        val previous = preferences.getString(ScanProtocol.REQUEST, "").orEmpty()
        return try {
            if (preferences.edit().putString(ScanProtocol.REQUEST, request).commit()) true
            else {
                preferences.edit().putString(ScanProtocol.REQUEST, previous).commit()
                false
            }
        } catch (_: RuntimeException) {
            try { preferences.edit().putString(ScanProtocol.REQUEST, previous).commit() }
            catch (_: RuntimeException) { /* 调用方展示保存失败并重新读取，不宣称预约成功。 */ }
            false
        }
    }
}

internal object ReportAdmission {
    fun allows(uid: Int, ownUid: Int, packages: List<String>): Boolean = uid == ownUid ||
        "com.huawei.health" in packages
}
