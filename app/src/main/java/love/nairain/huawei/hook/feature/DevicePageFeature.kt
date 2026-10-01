package love.nairain.huawei.hook.feature

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.HookContext
import love.nairain.huawei.hook.HookFeature
import love.nairain.huawei.hook.InstallResult
import love.nairain.huawei.hook.installIsolated
import love.nairain.huawei.hook.resolver.DeviceContentKeyResolver
import love.nairain.huawei.hook.resolver.ReflectionTargets
import love.nairain.huawei.hook.util.PageLayoutObserver
import love.nairain.huawei.hook.util.ActivePageObserver
import love.nairain.huawei.hook.util.ViewSelectors
import love.nairain.huawei.hook.util.ViewTrimmer
import java.util.WeakHashMap

class DevicePageFeature : HookFeature {
    override val id = "device.page"
    private val delegateRoots = WeakHashMap<View, String>()

    override fun install(context: HookContext): InstallResult {
        if (!SettingsCatalog.hasHidden(SettingsCategory.DEVICE, context.config)) {
            return InstallResult.Disabled
        }
        var count = 0
        context.points.deviceFragments.forEachIndexed { index, className ->
            count += context.installIsolated("device.fragment.$index") {
                installFragment(context, index, className)
            }
        }
        count += context.installIsolated("device.new.parent") {
            installPageLifecycle(context, "parent", context.points.newDeviceFragment) { applyNewParent(it, context) }
        }
        count += context.installIsolated("device.new.arkui") {
            installPageLifecycle(context, "arkui", context.points.arkuiDeviceFragment) { applyArkui(it, context) }
        }
        count += context.installIsolated("device.new.delegates") { installDelegateBinding(context) }
        count += context.installIsolated("device.new.store") {
            installPageLifecycle(context, "store", context.points.vmallFragment) { applyStore(it, context) }
        }
        ActivePageObserver.observe { activity ->
            val root = activity.window.decorView
            ViewSelectors.applyByResourceNames(root, context.application.packageName,
                DeviceContentKeyResolver.resourceMappings, context.config)
            applyLegacyAddCard(root, context)
            ViewSelectors.findByResourceName(root, context.application.packageName,
                "switch_device_layout")?.let { switcher ->
                PageLayoutObserver.observe(switcher) { view -> applyNewParent(view, context) }
                applyNewParent(root, context)
            }
            ViewSelectors.findByResourceName(root, context.application.packageName,
                "device_scrollview_content")?.let { content ->
                PageLayoutObserver.observe(content) { view -> applyArkui(view, context) }
                applyArkui(content, context)
            }
        }
        return if (count > 0) InstallResult.Installed(count)
        else InstallResult.Unsupported("verified device symbols unavailable")
    }

    private fun installFragment(context: HookContext, index: Int, className: String): Int {
        val type = ReflectionTargets.type(context.classLoader, className) ?: return 0
        var count = 0
        ReflectionTargets.method(type, "onCreateView", 3)?.let { method ->
            context.hooks.hook(method).setId("$id:create:$index")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    apply(result as? View, context)
                    result
                }
            count++
        }
        ReflectionTargets.method(type, "onResume", 0)?.let { method ->
            context.hooks.hook(method).setId("$id:resume:$index")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    apply(ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View, context)
                    result
                }
            count++
        }
        // 17.0.7.320 的营销资源刷新入口；每个入口独立安装，缺失时不影响生命周期 Hook。
        val refreshMethods = if (!love.nairain.huawei.hook.HookInstallPolicy.acceptsVersion(context.versionName, context.versionCode)) {
            emptyList()
        } else if (index == 0) {
            ReflectionTargets.methods(type, "a", 1).filter {
                List::class.java.isAssignableFrom(it.parameterTypes[0])
            }
        } else {
            ReflectionTargets.methods(type, "e", 2).filter {
                it.parameterTypes.lastOrNull()?.name == "java.util.Map"
            }
        }
        refreshMethods.forEachIndexed { refreshIndex, method ->
            context.hooks.hook(method).setId("$id:refresh:$index:$refreshIndex")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    apply(ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View, context)
                    result
                }
            count++
        }
        return count
    }

    private fun apply(root: View?, context: HookContext) {
        if (root == null) return
        PageLayoutObserver.observe(root) { view -> apply(view, context) }
        ViewSelectors.applyByResourceNames(
            root,
            context.application.packageName,
            DeviceContentKeyResolver.resourceMappings,
            context.config,
        )
        applyLegacyAddCard(root, context)
    }

    private fun applyLegacyAddCard(root: View, context: HookContext) {
        val packageName = context.application.packageName
        val wrapper = ViewSelectors.findByResourceName(root, packageName, "device_card") as? ViewGroup ?: return
        ViewSelectors.findByResourceName(wrapper, packageName, "device_card_normal") ?: return
        if (context.config[SettingsKeys.DEVICE_ADD] == true) ViewTrimmer.collapse(wrapper)
        else ViewTrimmer.restore(wrapper)
    }

    private fun installPageLifecycle(
        context: HookContext,
        source: String,
        className: String,
        apply: (View) -> Unit,
    ): Int {
        val type = ReflectionTargets.type(context.classLoader, className) ?: return 0
        var count = 0
        ReflectionTargets.method(type, "onCreateView", 3)?.let { method ->
            context.hooks.hook(method).setId("$id:$source:create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    (result as? View)?.let { root ->
                        PageLayoutObserver.observe(root, apply)
                        apply(root)
                        root.post { apply(root) }
                    }
                    result
                }
            count++
        }
        ReflectionTargets.method(type, "onResume", 0)?.let { method ->
            context.hooks.hook(method).setId("$id:$source:resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    (ReflectionTargets.invokeNoArgs(chain.thisObject, "getView") as? View)?.let { root ->
                        PageLayoutObserver.observe(root, apply)
                        apply(root)
                        root.post { apply(root) }
                    }
                    result
                }
            count++
        }
        return count
    }

    private fun installDelegateBinding(context: HookContext): Int {
        val type = ReflectionTargets.type(context.classLoader,
            "com.huawei.ui.homehealth.devicearkui.delegate.BaseViewDelegate") ?: return 0
        val method = ReflectionTargets.method(type, "obtainView", 2) ?: return 0
        context.hooks.hook(method).setId("$id:arkui-delegate")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val key = DeviceContentKeyResolver.arkuiDelegateKeys[chain.thisObject.javaClass.name]
                if (key != null && result is View) {
                    synchronized(delegateRoots) { delegateRoots[result] = key }
                    result.post {
                        if (ViewSelectors.hasAncestorResourceName(result, "hw_device_viewpager")) {
                            if (context.config[key] == true) ViewTrimmer.collapse(result)
                            else ViewTrimmer.restore(result)
                        }
                    }
                }
                result
            }
        return 1
    }

    private fun applyNewParent(root: View, context: HookContext) {
        val packageName = context.application.packageName
        ViewSelectors.applyByResourceNames(root, packageName,
            DeviceContentKeyResolver.newParentMappings, context.config)
        if (!love.nairain.huawei.hook.HookInstallPolicy.acceptsVersion(context.versionName, context.versionCode)) return
        val device = ViewSelectors.findByResourceName(root, packageName, "switch_device") ?: return
        val store = ViewSelectors.findByResourceName(root, packageName, "switch_web") ?: return
        val switcher = ViewSelectors.findByResourceName(root, packageName, "switch_device_layout") ?: return
        val hideSwitcher = context.config[SettingsKeys.DEVICE_SWITCHER] == true
        val hideStore = hideSwitcher || context.config[SettingsKeys.DEVICE_TAB_STORE] == true ||
            context.config[SettingsKeys.DEVICE_STORE] == true
        val hideDevice = !hideStore && context.config[SettingsKeys.DEVICE_TAB_DEVICE] == true
        if (hideStore && store.isSelected) device.performClick()
        if (hideDevice && device.isSelected) store.performClick()
        if (hideDevice) ViewTrimmer.collapse(device) else ViewTrimmer.restore(device)
        if (hideStore) ViewTrimmer.collapse(store) else ViewTrimmer.restore(store)
        if (hideSwitcher) ViewTrimmer.collapse(switcher) else ViewTrimmer.restore(switcher)
    }

    private fun applyArkui(root: View, context: HookContext) {
        if (!love.nairain.huawei.hook.HookInstallPolicy.acceptsVersion(context.versionName, context.versionCode)) return
        val content = ViewSelectors.findByResourceName(root, context.application.packageName,
            "device_scrollview_content") as? ViewGroup ?: return
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            val key = synchronized(delegateRoots) { delegateRoots[child] } ?: arkuiKeyByResource(child,
                context.application.packageName) ?: continue
            if (context.config[key] == true) ViewTrimmer.collapse(child) else ViewTrimmer.restore(child)
            if (key == SettingsKeys.DEVICE_PRIMARY) applyAddDevice(child, context)
            if (key == SettingsKeys.DEVICE_TIPS) applyTipRows(child, context)
        }
    }

    private fun arkuiKeyByResource(root: View, packageName: String): String? =
        mapOf(
            "rl_tab_device" to SettingsKeys.DEVICE_PRIMARY,
            "tab_all_device_layout" to SettingsKeys.DEVICE_LIST,
            "tab_setting_benefit_layout" to SettingsKeys.DEVICE_TIPS,
            "card_mywatch_view" to SettingsKeys.DEVICE_MY_WATCH,
            "card_watchface_view" to SettingsKeys.DEVICE_WATCH_FACES,
            "device_feature_container" to SettingsKeys.DEVICE_FEATURES,
        ).entries.firstOrNull { (name, _) ->
            ViewSelectors.findByResourceName(root, packageName, name) != null
        }?.value

    private fun applyAddDevice(root: View, context: HookContext) {
        ViewSelectors.findByResourceName(root, context.application.packageName, "ll_tab_device_empty")?.let {
            if (context.config[SettingsKeys.DEVICE_ADD] == true) ViewTrimmer.collapse(it)
            else ViewTrimmer.restore(it)
        }
    }

    private fun applyTipRows(root: View, context: HookContext) {
        ViewSelectors.walk(root) { view ->
            if (ViewSelectors.resourceEntryName(view) != "tab_title") return@walk
            val title = (view as? TextView)?.text?.toString() ?: return@walk
            val key = DeviceContentKeyResolver.arkuiSettings[title] ?: return@walk
            val row = view.parent as? View ?: return@walk
            if (context.config[key] == true) ViewTrimmer.collapse(row) else ViewTrimmer.restore(row)
        }
    }

    private fun applyStore(root: View, context: HookContext) {
        if (context.config[SettingsKeys.DEVICE_STORE] == true) ViewTrimmer.collapse(root)
        else ViewTrimmer.restore(root)
    }
}
