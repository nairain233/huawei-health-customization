@file:android.annotation.SuppressLint("DiscouragedApi") // 仅解析已核验宿主资源，不使用模块资源数值定位。

package love.nairain.huawei.hook.feature

import android.app.Activity
import android.app.Application
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.R
import love.nairain.huawei.hook.resolver.HostSettingsTargets
import love.nairain.huawei.hook.util.ActivePageObserver
import love.nairain.huawei.hook.util.LayoutCallbackScope
import love.nairain.huawei.hook.util.ModuleLogger
import love.nairain.huawei.settings.HostSettingsController
import love.nairain.huawei.settings.HostRestarter
import love.nairain.huawei.settings.HostSettingsDialog
import love.nairain.huawei.settings.HostSettingsPageState
import love.nairain.huawei.watchface.ModuleResources
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 独立于精简开关及 DexKit 安装；只作用于已核验版本的原生设置 Activity。 */
internal object HostSettingsFeature {
    fun install(framework: XposedInterface, loader: ClassLoader, app: Application,
        version: String?, code: Long, logger: ModuleLogger, client: HostSettingsController, restarter: HostRestarter) {
        if (!HostSettingsTargets.accepts(version, code)) {
            logger.info("Host settings unsupported; original settings retained")
            return
        }
        val main = Handler(Looper.getMainLooper())
        Thread({
            runCatching {
                val targets = HostSettingsTargets(loader, version, code)
                val resources = ModuleResources.load(app.packageManager, framework.moduleApplicationInfo)
                main.post {
                    val scope = LayoutCallbackScope { reason -> logger.warn("Host settings disabled: $reason") }
                    scope.afterActivation {
                        Runtime(app, targets, resources, client, restarter, scope, logger).register()
                    }
                    scope.activate()
                }
            }.onFailure { logger.warn("Host settings unavailable: ${it.javaClass.simpleName}") }
        }, "HostSettingsPrepare").apply { isDaemon = true }.start()
    }

    private class Runtime(private val app: Application, private val targets: HostSettingsTargets,
        private val moduleResources: Resources, private val client: HostSettingsController, private val restarter: HostRestarter,
        private val scope: LayoutCallbackScope, private val logger: ModuleLogger) : Application.ActivityLifecycleCallbacks {
        private class Page {
            val state = HostSettingsPageState()
            var dialog = WeakReference<HostSettingsDialog>(null)
            var entry = WeakReference<View>(null)
        }
        // 值只保留弱窗口引用及基础类型状态，不形成 WeakHashMap 的 value→key 强引用环。
        private val pages = WeakHashMap<Activity, Page>()
        fun register() {
            app.registerActivityLifecycleCallbacks(this)
            scope.onClose {
                app.unregisterActivityLifecycleCallbacks(this)
                pages.values.toList().forEach { page ->
                    page.dialog.get()?.dispose()
                    page.entry.get()?.let { entry -> (entry.parent as? ViewGroup)?.removeView(entry) }
                }
                pages.clear()
                client.stop()
            }
            ActivePageObserver.observe(scope, ::apply)
            logger.info("Host settings observer ready")
        }
        private fun apply(activity: Activity) = scope.run {
            val (container, goal) = targets.container(activity) ?: return@run
            if (container !is LinearLayout) return@run
            val page = pages.getOrPut(activity) { Page() }
            val entry = page.entry.get()
            if (entry?.parent !== container) {
                val titleId = activity.resources.getIdentifier("goal_text_view", "id", activity.packageName)
                val arrowId = activity.resources.getIdentifier("goal_image_view", "id", activity.packageName)
                val title = HostSettingsTargets.descendants(goal).filter { it.id == titleId }.singleOrNull() as? TextView ?: return@run
                val arrow = HostSettingsTargets.descendants(goal).filter { it.id == arrowId }.singleOrNull() as? ImageView ?: return@run
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = goal.minimumHeight
                    background = goal.background?.constantState?.newDrawable(activity.resources)?.mutate()
                    isFocusable = true
                    val padding = (16 * activity.resources.displayMetrics.density).toInt()
                    setPaddingRelative(padding, padding, padding, padding)
                    tag = "love.nairain.huawei.host-settings-entry"
                }
                row.addView(targets.text(activity).apply {
                    val localized = ModuleResources.localized(moduleResources, activity.resources)
                    text = localized.getString(R.string.settings_title)
                    setTextColor(title.textColors); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, title.textSize)
                    typeface = title.typeface
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(ImageView(activity).apply {
                    setImageDrawable(arrow.drawable?.constantState?.newDrawable(activity.resources)?.mutate())
                    imageTintList = arrow.imageTintList
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(-2, -2))
                row.setOnClickListener { scope.run { show(activity, page) } }
                val wrapper = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(row, LinearLayout.LayoutParams(-1, -2))
                    addView(targets.divider(activity), LinearLayout.LayoutParams(-1, (activity.resources.displayMetrics.density).toInt().coerceAtLeast(1)))
                }
                // 只插入模块新建节点，不移动目标行、不复用其 ID 或点击监听器。
                container.addView(wrapper, container.indexOfChild(goal), LinearLayout.LayoutParams(-1, -2))
                page.entry = WeakReference(wrapper)
                logger.info("Host settings entry attached")
            }
            if (page.state.open && page.dialog.get() == null) show(activity, page)
            else page.dialog.get()?.resume()
        }
        private fun show(activity: Activity, page: Page) {
            if (activity.isFinishing || activity.isDestroyed) return
            page.dialog.get()?.takeIf { it.isShowing }?.let { return }
            val dialog = HostSettingsDialog(activity, targets, moduleResources, client, page.state, restarter) { error ->
                scope.run { throw error }
            }
            page.dialog = WeakReference(dialog)
            dialog.show()
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (targets.activity.isInstance(activity)) pages.getOrPut(activity) { Page() }.state.restore(savedInstanceState?.getBundle(STATE))
        }
        override fun onActivityResumed(activity: Activity) = apply(activity)
        override fun onActivityStopped(activity: Activity) { pages[activity]?.dialog?.get()?.pause() }
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            pages[activity]?.let { page ->
                page.dialog.get()?.captureState()
                outState.putBundle(STATE, page.state.save())
            }
        }
        override fun onActivityDestroyed(activity: Activity) { pages.remove(activity)?.dialog?.get()?.dispose() }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
    }
    private const val STATE = "love.nairain.huawei.host-settings-state"
}
