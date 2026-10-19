package love.nairain.huawei.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Process
import androidx.annotation.MainThread
import love.nairain.huawei.hook.HookInstallPolicy

/** 用户主动重启主进程；启动请求返回不等于新进程已拉起，时序仍须设备验证。 */
@MainThread
internal class HostRestarter(
    private val packageName: String,
    private val processName: () -> String?,
    private val configurationReady: () -> Boolean,
    private val launch: () -> Boolean,
    private val terminate: () -> Unit,
    private val diagnostic: (String) -> Unit,
) {
    enum class Result { REQUESTED, ALREADY_REQUESTED, CONFIGURATION_BLOCKED, WATCH_FACE_BUSY, FAILED }

    private var requested = false
    private var watchFaceBusy: () -> Boolean = { false }
    private var observer: (() -> Unit)? = null
    val waitingForWatchFace: Boolean get() = runCatching(watchFaceBusy).getOrDefault(true)
    val available: Boolean get() = !requested && configurationReady() && !waitingForWatchFace

    /** 保留查询直到进程结束；页面关闭或 Hook 撤销不代表原生传输已停止。 */
    fun setWatchFaceBusyCheck(check: () -> Boolean) {
        watchFaceBusy = check
        watchFaceStateChanged()
    }

    fun observe(changed: () -> Unit): AutoCloseable {
        observer = changed
        watchFaceStateChanged()
        return AutoCloseable { if (observer === changed) observer = null }
    }

    fun watchFaceStateChanged() {
        // 配置页的显示失败不能阻断表盘任务的状态推进。
        runCatching { observer?.invoke() }.onFailure {
            diagnostic("Host restart observer failed: ${it.javaClass.simpleName}")
        }
    }

    fun buttonUnavailable(error: Throwable) {
        diagnostic("Host restart button unavailable: ${error.javaClass.simpleName}")
    }

    fun restart(beforeLaunch: () -> Unit): Result {
        if (requested) return Result.ALREADY_REQUESTED
        if (packageName != HookInstallPolicy.TARGET_PACKAGE || runCatching(processName).getOrNull() != HookInstallPolicy.TARGET_PACKAGE) {
            diagnostic("Host restart rejected: process_guard")
            return Result.FAILED
        }
        if (!configurationReady()) return Result.CONFIGURATION_BLOCKED
        if (waitingForWatchFace) return Result.WATCH_FACE_BUSY
        requested = true
        return try {
            beforeLaunch()
            if (!launch()) {
                requested = false
                diagnostic("Host restart unavailable: launcher_invalid")
                Result.FAILED
            } else {
                terminate()
                Result.REQUESTED
            }
        } catch (error: Exception) {
            requested = false
            diagnostic("Host restart failed: ${error.javaClass.simpleName}")
            Result.FAILED
        }
    }

    companion object {
        @android.annotation.SuppressLint("QueryPermissionsNeeded") // 主进程守卫通过后只查询宿主自身，无需模块扩大包可见范围。
        fun create(app: Context, configurationReady: () -> Boolean, diagnostic: (String) -> Unit) = HostRestarter(
            app.packageName, Application::getProcessName, configurationReady,
            launch = {
                val intent = app.packageManager.getLaunchIntentForPackage(HookInstallPolicy.TARGET_PACKAGE)
                @Suppress("DEPRECATION")
                val resolved = intent?.resolveActivityInfo(app.packageManager, 0)
                val verified = validatedLauncher(intent, resolved)
                if (verified == null) false
                else {
                    app.startActivity(verified)
                    true
                }
            },
            terminate = { Process.killProcess(Process.myPid()) }, diagnostic = diagnostic,
        )

        /** 只使用 PackageManager 返回且解析回宿主自身的显式入口，不接受选择器或外部组件。 */
        fun validatedLauncher(intent: Intent?, resolved: ActivityInfo?): Intent? {
            val component = intent?.component ?: return null
            if (intent.selector != null || (intent.`package` != null && intent.`package` != HookInstallPolicy.TARGET_PACKAGE) ||
                component.packageName != HookInstallPolicy.TARGET_PACKAGE ||
                resolved?.packageName != HookInstallPolicy.TARGET_PACKAGE || resolved.name != component.className ||
                !resolved.enabled || resolved.applicationInfo?.enabled != true) return null
            return Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }
}
