package love.nairain.huawei.settings

import android.app.Activity
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * 宿主窗口内持续存活的模块 Compose 容器，所有页面共享同一主题与窗口状态。
 * 不依赖宿主的 AndroidX owner，不修改宿主 ClassLoader；模块资源限定在本子树。
 */
internal class HostSettingsComposeHost(
    activity: Activity,
    resources: Resources,
    private val page: HostSettingsPageState,
    initialModel: HostSettingsUiState,
    action: (HostSettingsAction) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val owner = HostComposeOwner()
    private val model = mutableStateOf(initialModel)
    private val scrolls = mutableMapOf<String, ScrollState>()
    private val customList = LazyListState(page.customIndex, page.customOffset)
    private var closed = false
    private var failing = false
    private var recomposer: Recomposer? = null
    private var compose: ComposeView? = null
    val view: FrameLayout = HostComposeFrame(HostComposeContext(activity, resources), ::reportFailure)

    init {
        try {
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            view.setViewTreeNavigationEventDispatcherOwner(owner)
            val content = ComposeView(view.context).also { compose = it }
            val handler = CoroutineExceptionHandler { _, error -> reportFailure(error) }
            recomposer = view.createLifecycleAwareWindowRecomposer(handler, owner.lifecycle)
            content.setParentCompositionContext(recomposer)
            content.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            content.setContent {
                val current = model.value
                val scroll = scrolls.getOrPut(current.route) { ScrollState(page.scroll[current.route] ?: 0) }
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                    HostSettingsScreen(current, resources, scroll, customList, action)
                }
            }
            view.addView(content, ViewGroup.LayoutParams(-1, -1))
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun captureState() {
        scrolls.forEach { (route, scroll) -> page.scroll[route] = scroll.value }
        page.customIndex = customList.firstVisibleItemIndex
        page.customOffset = customList.firstVisibleItemScrollOffset
    }

    fun update(value: HostSettingsUiState) { if (!closed && !failing) model.value = value }

    fun resume() { if (!closed) owner.moveTo(Lifecycle.State.RESUMED) }

    fun pause() { if (!closed) owner.moveTo(Lifecycle.State.CREATED) }

    private fun reportFailure(error: Throwable) {
        if (closed || failing) return
        failing = true
        // 不在 Compose 重组或 View 遍历期间更换子树。
        main.post { if (!closed) onFailure(error) }
    }

    override fun close() {
        if (closed) return
        closed = true
        main.removeCallbacksAndMessages(null)
        try { compose?.disposeComposition() }
        finally {
            recomposer?.cancel()
            owner.moveTo(Lifecycle.State.DESTROYED)
            compose = null
            recomposer = null
        }
    }
}

/** 生命周期随本窗口开启、停止和销毁；业务快照与路由不存入 Compose Registry。 */
internal class HostComposeOwner : SavedStateRegistryOwner, NavigationEventDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = savedState.savedStateRegistry
    override val navigationEventDispatcher = NavigationEventDispatcher()

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun moveTo(state: Lifecycle.State) {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = state
            if (state == Lifecycle.State.DESTROYED) navigationEventDispatcher.dispose()
        }
    }
}

/** Compose 的 Android 资源来自模块 APK；窗口、服务和输入法仍使用宿主 Activity。 */
private class HostComposeContext(activity: Activity, resources: Resources) :
    ContextThemeWrapper(activity, 0) {
    private val moduleResources = resources
    private val moduleTheme by lazy {
        val dark = moduleResources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        moduleResources.newTheme().apply {
            applyStyle(if (dark) android.R.style.Theme_DeviceDefault_NoActionBar else android.R.style.Theme_DeviceDefault_Light_NoActionBar, true)
        }
    }
    override fun getResources(): Resources = moduleResources
    override fun getAssets(): AssetManager = moduleResources.assets
    override fun getTheme(): Resources.Theme = moduleTheme
    override fun getClassLoader(): ClassLoader = HostSettingsComposeHost::class.java.classLoader!!
}

/** 初次组合之外的测量、布局和绘制失败只撤回本窗口的 Compose 子树。 */
@android.annotation.SuppressLint("ViewConstructor") // 仅由模块代码创建，不供 XML 或布局编辑器实例化。
private class HostComposeFrame(context: Context, onFailure: (Throwable) -> Unit) : FrameLayout(context) {
    private val reportFailure = onFailure
    init { layoutDirection = context.resources.configuration.layoutDirection }
    @Suppress("DEPRECATION") // 只更新模块子树的资源，不修改宿主 Configuration。
    override fun onConfigurationChanged(newConfig: Configuration) {
        context.resources.updateConfiguration(newConfig, context.resources.displayMetrics)
        layoutDirection = newConfig.layoutDirection
        super.onConfigurationChanged(newConfig)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        try { super.onMeasure(widthMeasureSpec, heightMeasureSpec) }
        catch (error: Throwable) {
            setMeasuredDimension(0, 0)
            reportFailure(error)
        }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        try { super.onLayout(changed, left, top, right, bottom) }
        catch (error: Throwable) { reportFailure(error) }
    }
    override fun dispatchDraw(canvas: Canvas) {
        try { super.dispatchDraw(canvas) }
        catch (error: Throwable) { reportFailure(error) }
    }
}
