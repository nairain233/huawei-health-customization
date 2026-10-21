package love.nairain.huawei.hook.feature

import android.app.Activity
import android.content.res.Resources
import android.view.View
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.R
import love.nairain.huawei.hook.TransactionalHooks
import love.nairain.huawei.hook.resolver.ActiveFragmentTargets
import love.nairain.huawei.hook.resolver.HomeMoreTargets
import love.nairain.huawei.hook.util.ActivePageObserver
import love.nairain.huawei.hook.util.LayoutCallbackScope
import love.nairain.huawei.hook.util.ModuleLogger
import love.nairain.huawei.settings.HostSettingsWindows
import love.nairain.huawei.watchface.ModuleResources
import java.lang.ref.WeakReference

/** 独立扫描首页更多入口；失败只停用本组，宿主点击保持原样。 */
internal object HomeMoreSettingsFeature {
    fun install(framework: XposedInterface, targets: HomeMoreTargets, windows: HostSettingsWindows,
        resources: Resources, logger: ModuleLogger) {
        runCatching {
            val hooks = TransactionalHooks(framework, logger)
            val count = hooks.install("host-settings.home-more") {
                val scope = hooks.callbacks()
                val runtime = Runtime(targets, windows, resources, scope, logger)
                hooks.hook(targets.createView).setId("huawei.settings.home-more:create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                        val root = chain.proceed()
                        scope.run {
                            val activity = chain.thisObject?.let(targets::activity)
                            if (root is View && activity != null && targets.ownsTitle(chain.thisObject!!, root)) runtime.observe(root, activity)
                        }
                        root
                    }
                hooks.hook(targets.bind).setId("huawei.settings.home-more:bind")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                        val result = chain.proceed()
                        scope.run {
                            chain.thisObject?.let { fragment ->
                                val activity = targets.activity(fragment)
                                val root = targets.root(fragment)
                                if (activity != null && root != null && targets.ownsTitle(fragment, root)) runtime.observe(root, activity)
                            }
                        }
                        result
                    }
                scope.afterActivation {
                    ActivePageObserver.observe(scope) { activity ->
                        ActiveFragmentTargets.forEach(activity, targets.fragment) { fragment ->
                            targets.root(fragment)?.takeIf { targets.ownsTitle(fragment, it) }?.let { runtime.observe(it, activity) }
                        }
                    }
                }
                2
            }
            if (count > 0) logger.info("Home more observer ready")
        }.onFailure { logger.warn("Home more entry unavailable: " + it.javaClass.simpleName) }
    }

    private class Runtime(private val targets: HomeMoreTargets, private val windows: HostSettingsWindows,
        private val resources: Resources, private val scope: LayoutCallbackScope, private val logger: ModuleLogger) {
        private val presses = HomeMoreLongPress { error ->
            logger.warn("Home more callback unavailable: " + error.javaClass.simpleName)
        }.also { scope.onClose(it::close) }
        private val pages = HomeMorePageObserver(scope, ::bind)
        fun observe(root: View, activity: Activity) {
            pages.observe(root, activity)
        }
        private fun bind(root: View, activity: Activity) {
            val owner = WeakReference(activity)
            val button = targets.button(root) ?: return
            val pageRef = WeakReference(root)
            presses.bind(button) { clicked ->
                val current = owner.get()?.takeUnless { it.isDestroyed || it.isFinishing }
                if (scope.isActive && current != null) {
                    val opened = runCatching {
                        val valid = pageRef.get()?.let { targets.button(it) === clicked } == true
                        valid && windows.show(current)
                    }.onFailure { logger.warn("Home more open unavailable: " + it.javaClass.simpleName) }.getOrDefault(false)
                    if (!opened) runCatching {
                        val strings = ModuleResources.localized(resources, current.resources)
                        Toast.makeText(current, strings.getString(R.string.host_settings_open_failed), Toast.LENGTH_SHORT).show()
                    }.onFailure { logger.warn("Home more notice unavailable: " + it.javaClass.simpleName) }
                }
            }
        }
    }
}
