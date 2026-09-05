package love.nairain.huawei.hook.util

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * 统一脱敏日志入口，不记录业务或隐私数据。
 */
class ModuleLogger(
    private val framework: XposedInterface,
) {
    private val tag: String = "HuaweiTrim"

    fun info(message: String) {
        framework.log(Log.INFO, tag, message)
    }

    fun warn(message: String) {
        framework.log(Log.WARN, tag, message)
    }

    fun warn(message: String, error: Throwable) {
        framework.log(Log.WARN, tag, message, error)
    }
}
