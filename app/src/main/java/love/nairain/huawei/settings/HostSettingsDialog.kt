package love.nairain.huawei.settings

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import love.nairain.huawei.R
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.scan.ScanReport
import love.nairain.huawei.watchface.ModuleResources
import java.io.File
import java.util.concurrent.Executors

/** 非浮动全屏页面只管理边界、生命周期与既有命令；配置界面由一个 Compose 子树提供。 */
@Suppress("DEPRECATION") // 保留 API 28 起可用的窗口 IME resize 行为。
internal class HostSettingsDialog(
    private val activity: Activity,
    moduleResources: Resources,
    private val client: HostSettingsController,
    private val state: HostSettingsPageState,
    private val restarter: HostRestarter,
    private val composeFailed: (Throwable) -> Unit = {},
    private val failed: (Throwable) -> Unit,
) : Dialog(activity, if (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    android.R.style.Theme_DeviceDefault_NoActionBar else android.R.style.Theme_DeviceDefault_Light_NoActionBar), HostSettingsWindow {
    override val visible: Boolean get() = isShowing
    private val strings = ModuleResources.localized(moduleResources, activity.resources)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "HostSettingsReport").apply { isDaemon = true } }
    private val root = FrameLayout(activity)
    private val session = HostSettingsSession(state, client, ::update, ::effect)
    private var compose: HostSettingsComposeHost? = null
    private var subscription: AutoCloseable? = null
    private var restartSubscription: AutoCloseable? = null
    private var active = false
    private var disposed = false
    private var errorMode = false
    private var generation = 0L
    private var composeGeneration = 0L
    private var backCallback: Any? = null
    private val refreshReport = object : Runnable {
        override fun run() {
            if (!active || disposed || errorMode || state.route != "scan") return
            val expected = generation
            worker.execute {
                val report = runCatching {
                    AtomicFile(File(HostStorage.scanDirectory(activity), "report.json")).openRead().bufferedReader().use { reader ->
                        val chars = CharArray(32769)
                        var size = 0
                        while (size < chars.size) {
                            val count = reader.read(chars, size, chars.size - size)
                            if (count < 0) break
                            size += count
                        }
                        require(size <= 32768)
                        ScanReport.decode(String(chars, 0, size))
                    }
                }.getOrNull()
                val invalid = runCatching {
                    @Suppress("DEPRECATION")
                    val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
                    report != null && (report.versionCode != info.longVersionCode || report.updated != info.lastUpdateTime || report.rules != ScanProtocol.RULES)
                }.getOrDefault(true)
                main.post {
                    if (active && !disposed && !errorMode && expected == generation && state.route == "scan")
                        session.report(report, invalid, System.currentTimeMillis())
                }
            }
            main.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.requestFeature(android.view.Window.FEATURE_NO_TITLE)
        val dark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        root.setBackgroundColor(if (dark) Color.rgb(18, 18, 18) else Color.rgb(247, 247, 247))
        // 全屏边界统一在平台根容器消费，Compose 不再重复叠加系统栏或输入法 Insets。
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
        setCanceledOnTouchOutside(false)
        setOnDismissListener { state.open = false; dispose() }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val callback = android.window.OnBackInvokedCallback { back() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        }
        installCompose()
    }

    override fun show() {
        if (disposed) return
        state.open = true
        super.show()
        if (disposed) { dismiss(); return }
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            val dark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(this, decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        ViewCompat.requestApplyInsets(root)
        resume()
    }

    override fun resume() {
        if (active || disposed || errorMode) return
        active = true
        session.resume()
        compose?.resume()
        subscription = client.start { value -> safely { session.accept(value) } }
        restartSubscription = restarter.observe { safely { session.restartState(restarter.available, restarter.waitingForWatchFace) } }
        refreshScan()
    }

    override fun pause() {
        if (!active) return
        active = false
        captureState()
        session.pause()
        compose?.pause()
        subscription?.close(); subscription = null
        restartSubscription?.close(); restartSubscription = null
        main.removeCallbacks(refreshReport)
        generation++
    }

    override fun captureState() { compose?.captureState() }

    override fun dispose() {
        if (disposed) return
        pause()
        disposed = true
        closeCompose()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            (backCallback as? android.window.OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        }
        backCallback = null
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        if (isShowing) { dismiss() }
    }

    @android.annotation.SuppressLint("GestureBackNavigation") // API 33+ 由本窗口的 OnBackInvokedCallback 处理。
    @Deprecated("API 32 及以下返回键兼容")
    override fun onBackPressed() = back()

    private fun back() {
        if (errorMode) dismiss() else dispatch(HostSettingsAction.Back)
    }

    private fun dispatch(action: HostSettingsAction) = safely {
        if (disposed || errorMode) return@safely
        captureState()
        session.dispatch(action)
    }

    private fun effect(action: HostSettingsAction) {
        when (action) {
            is HostSettingsAction.Navigate -> refreshScan()
            HostSettingsAction.Close -> dismiss()
            HostSettingsAction.Restart -> {
                val result = restarter.restart { toast(R.string.host_restart_starting) }
                when (result) {
                    HostRestarter.Result.WATCH_FACE_BUSY -> toast(R.string.host_restart_watchface_busy)
                    HostRestarter.Result.FAILED -> toast(R.string.host_restart_failed)
                    else -> Unit
                }
                session.restartState(restarter.available, restarter.waitingForWatchFace)
            }
            is HostSettingsAction.OpenLink -> runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, action.url.toUri())) }
                .onFailure { toast(R.string.about_link_failed) }
            else -> Unit
        }
    }

    private fun refreshScan() {
        main.removeCallbacks(refreshReport)
        if (active && state.route == "scan" && !errorMode) main.post(refreshReport)
    }

    private fun update() { if (!disposed && !errorMode) compose?.update(session.model()) }

    private fun installCompose() {
        if (disposed) return
        errorMode = false
        val expected = ++composeGeneration
        try {
            val content = HostSettingsComposeHost(activity, strings, state, session.model(), ::dispatch) { error ->
                if (!disposed && composeGeneration == expected) showError(error)
            }
            compose = content
            root.removeAllViews()
            root.addView(content.view, FrameLayout.LayoutParams(-1, -1))
            if (active) content.resume()
        } catch (error: Throwable) { showError(error) }
    }

    /** 完全独立于 Compose 的最小降级窗口；重试只重建显示，不调用任何写命令。 */
    private fun showError(error: Throwable) {
        if (disposed || errorMode) return
        errorMode = true
        composeFailed(error)
        pause()
        closeCompose()
        root.removeAllViews()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        content.addView(TextView(activity).apply {
            text = strings.getString(R.string.host_ui_framework_error)
            textSize = 16f
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(Button(activity).apply {
            text = strings.getString(R.string.host_ui_retry)
            setOnClickListener { safely { installCompose(); if (!errorMode) resume() } }
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(Button(activity).apply {
            text = strings.getString(R.string.close)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    private fun closeCompose() {
        composeGeneration++
        val old = compose ?: return
        compose = null
        runCatching { old.captureState() }.onFailure(composeFailed)
        runCatching { old.close() }.onFailure(composeFailed)
    }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun toast(resource: Int) = Toast.makeText(activity, strings.getString(resource), Toast.LENGTH_LONG).show()
    private fun safely(action: () -> Unit) {
        try { action() } catch (error: Throwable) { dispose(); failed(error) }
    }
}
