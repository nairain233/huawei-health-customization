package love.nairain.huawei.scan

import android.content.SharedPreferences
import android.annotation.SuppressLint

/** RemotePreferences 可能先修改本地缓存；失败时恢复旧值，不能乐观显示已预约。 */
internal enum class ScanRequestOutcome { SUCCESS, RESTORED, UNCERTAIN }

internal object ScanRequests {
    @SuppressLint("ApplySharedPref", "UseKtx") // 工作线程需要 commit 返回值确认预约与回滚是否落盘。
    fun save(
        preferences: SharedPreferences,
        request: String,
        canWrite: () -> Boolean = { true },
    ): ScanRequestOutcome {
        val existed: Boolean
        val previous: String?
        try {
            existed = preferences.contains(ScanProtocol.REQUEST)
            previous = preferences.getString(ScanProtocol.REQUEST, null)
        } catch (_: RuntimeException) {
            // 尚未写入；调用方显示失败，允许重试。
            return ScanRequestOutcome.RESTORED
        }
        val saved = try {
            val editor = preferences.edit().putString(ScanProtocol.REQUEST, request)
            // 读取原值期间也可能停止或换绑；此处是首次提交的最后一道守卫。
            if (!canWrite()) return ScanRequestOutcome.RESTORED
            editor.commit()
        } catch (_: RuntimeException) { false }
        if (saved) return ScanRequestOutcome.SUCCESS
        val restored = try {
            val editor = preferences.edit()
            if (existed) editor.putString(ScanProtocol.REQUEST, previous)
            else editor.remove(ScanProtocol.REQUEST)
            editor.commit()
        } catch (_: RuntimeException) { false }
        // 两次提交都必须确认；缓存中的旧值不能证明远端已恢复。
        return if (restored) ScanRequestOutcome.RESTORED else ScanRequestOutcome.UNCERTAIN
    }
}

internal object ReportAdmission {
    fun allows(uid: Int, ownUid: Int, packages: List<String>): Boolean = uid == ownUid ||
        "com.huawei.health" in packages
}
