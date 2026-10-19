package love.nairain.huawei.settings

import android.app.Activity
import android.app.Application
import android.content.res.Resources
import android.os.Bundle
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 两个入口共用窗口及生命周期；入口安装组失败不关闭其他入口的窗口管理。仅在主线程调用。 */
internal class HostSettingsWindows(
    private val create: (Activity, HostSettingsPageState, (Throwable) -> Unit) -> HostSettingsWindow,
    private val diagnostic: (String) -> Unit,
) : Application.ActivityLifecycleCallbacks {
    constructor(resources: Resources, client: HostSettingsController,
        restarter: HostRestarter, diagnostic: (String) -> Unit) : this({ activity, state, failed ->
        HostSettingsDialog(activity, resources, client, state, restarter,
            composeFailed = { diagnostic("Host settings Miuix fallback: " + it.javaClass.simpleName) }, failed = failed)
    }, diagnostic)

    private class Page {
        val state = HostSettingsPageState()
        var dialog = WeakReference<HostSettingsWindow>(null)
        var generation = 0L
    }
    // 值不持有 Activity、窗口或 View 的强引用。
    private val pages = WeakHashMap<Activity, Page>()

    fun show(activity: Activity): Boolean = show(activity, pages.getOrPut(activity) { Page() }, restoring = false)

    private fun show(activity: Activity, page: Page, restoring: Boolean): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        page.dialog.get()?.takeIf { it.visible }?.let { return true }
        if (!restoring) {
            page.state.resetNavigation()
        }
        val generation = ++page.generation
        return try {
            val dialog = create(activity, page.state) { if (page.generation == generation) fail(page, it) }
            page.dialog = WeakReference(dialog)
            dialog.show()
            // onCreate 内部安全回调可能已处置本窗口。
            page.state.open && dialog.visible
        } catch (error: Throwable) {
            fail(page, error)
            false
        }
    }

    private fun fail(page: Page, error: Throwable) {
        diagnostic("Host settings window unavailable: " + error.javaClass.simpleName)
        page.generation++
        page.state.open = false
        val dialog = page.dialog.get()
        page.dialog.clear()
        runCatching { dialog?.dispose() }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        savedInstanceState?.getBundle(STATE)?.let { saved ->
            pages.getOrPut(activity) { Page() }.state.restore(saved)
        }
    }
    override fun onActivityResumed(activity: Activity) {
        val page = pages[activity] ?: return
        if (page.state.open && page.dialog.get() == null) show(activity, page, restoring = true)
        else runCatching { page.dialog.get()?.resume() }.onFailure { fail(page, it) }
    }
    override fun onActivityStopped(activity: Activity) {
        pages[activity]?.let { page -> runCatching { page.dialog.get()?.pause() }.onFailure { fail(page, it) } }
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        pages[activity]?.let { page ->
            runCatching { page.dialog.get()?.captureState(); outState.putBundle(STATE, page.state.save()) }
                .onFailure { fail(page, it) }
        }
    }
    override fun onActivityDestroyed(activity: Activity) {
        pages.remove(activity)?.let { page -> page.generation++; runCatching { page.dialog.get()?.dispose() }; page.dialog.clear() }
    }
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    private companion object { const val STATE = "love.nairain.huawei.host-settings-state" }
}

/** 窗口管理只关心生命周期；原生窗口和设备测试使用同一契约。 */
internal interface HostSettingsWindow {
    val visible: Boolean
    fun show()
    fun resume()
    fun pause()
    fun captureState()
    fun dispose()
}
