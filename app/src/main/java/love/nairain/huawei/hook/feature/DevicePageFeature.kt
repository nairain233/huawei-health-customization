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
import love.nairain.huawei.hook.installSource
import love.nairain.huawei.hook.resolver.DeviceContentKeyResolver
import love.nairain.huawei.hook.util.ActivePageObserver
import love.nairain.huawei.hook.util.applyPage
import love.nairain.huawei.hook.util.post
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
        fun installSource(source: String, install: (HookContext) -> Int) {
            count += context.installSource(source, install)
        }
        context.points.deviceFragments.forEachIndexed { index, className ->
            installSource("device.fragment.$index") { scoped ->
                installFragment(scoped, index, className)
            }
        }
        installSource("device.new.parent") { scoped ->
            installPageLifecycle(scoped, "parent", context.points.newDeviceFragment) { applyNewParent(it, scoped) }
        }
        installSource("device.new.arkui") { scoped ->
            installPageLifecycle(scoped, "arkui", context.points.arkuiDeviceFragment) { applyArkui(it, scoped) }
        }
        installSource("device.new.delegates", ::installDelegateBinding)
        installSource("device.new.store") { scoped ->
            installPageLifecycle(scoped, "store", context.points.vmallFragment) { applyStore(it, scoped) }
        }
        return if (count > 0) InstallResult.Installed(count)
        else InstallResult.Unsupported("verified device symbols unavailable")
    }

    private fun installFragment(context: HookContext, index: Int, className: String): Int {
        val type = context.targets.type(context.classLoader, className) ?: return 0
        val callbacks = context.hooks.callbacks()
        ActivePageObserver.observeFragment(callbacks, type) { view -> apply(view, context) }
        var count = 0
        context.targets.method(type, "onCreateView", 3)?.let { method ->
            context.hooks.hook(method).setId("$id:create:$index")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    callbacks.applyPage(result as? View) { view -> apply(view, context) }
                    result
                }
            count++
        }
        context.targets.method(type, "onResume", 0)?.let { method ->
            context.hooks.hook(method).setId("$id:resume:$index")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    callbacks.applyPage(context.targets.invokeNoArgs(chain.thisObject, "getView") as? View) {
                        view -> apply(view, context)
                    }
                    result
                }
            count++
        }
        val refreshMethods = context.targets.methods(type, "device.refresh", if (index == 0) 1 else 2)
        refreshMethods.forEachIndexed { refreshIndex, method ->
            context.hooks.hook(method).setId("$id:refresh:$index:$refreshIndex")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    callbacks.applyPage(context.targets.invokeNoArgs(chain.thisObject, "getView") as? View) {
                        view -> apply(view, context)
                    }
                    result
                }
            count++
        }
        return count
    }

    private fun apply(root: View?, context: HookContext) {
        if (root == null) return
        context.views.applyByResourceNames(
            root,
            context.application.packageName,
            DeviceContentKeyResolver.resourceMappings,
            context.config,
        )
        applyLegacyAddCard(root, context)
    }

    private fun applyLegacyAddCard(root: View, context: HookContext) {
        val packageName = context.application.packageName
        val wrapper = context.views.findByResourceName(root, packageName, "device_card") as? ViewGroup ?: return
        context.views.findByResourceName(wrapper, packageName, "device_card_normal") ?: return
        if (context.config[SettingsKeys.DEVICE_ADD] == true) ViewTrimmer.collapse(wrapper)
        else ViewTrimmer.restore(wrapper)
    }

    private fun installPageLifecycle(
        context: HookContext,
        source: String,
        className: String,
        apply: (View) -> Unit,
    ): Int {
        val type = context.targets.type(context.classLoader, className) ?: return 0
        val callbacks = context.hooks.callbacks()
        ActivePageObserver.observeFragment(callbacks, type, apply)
        var count = 0
        context.targets.method(type, "onCreateView", 3)?.let { method ->
            context.hooks.hook(method).setId("$id:$source:create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    callbacks.applyPage(result as? View, apply)
                    result
                }
            count++
        }
        context.targets.method(type, "onResume", 0)?.let { method ->
            context.hooks.hook(method).setId("$id:$source:resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    callbacks.applyPage(context.targets.invokeNoArgs(chain.thisObject, "getView") as? View, apply)
                    result
                }
            count++
        }
        return count
    }

    private fun installDelegateBinding(context: HookContext): Int {
        val type = context.targets.type(context.classLoader,
            "com.huawei.ui.homehealth.devicearkui.delegate.BaseViewDelegate") ?: return 0
        val method = context.targets.method(type, "obtainView", 2) ?: return 0
        val callbacks = context.hooks.callbacks()
        context.hooks.hook(method).setId("$id:arkui-delegate")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val key = context.targets.identities["delegate:${chain.thisObject.javaClass.name}"]
                if (key != null && result is View) {
                    synchronized(delegateRoots) { delegateRoots[result] = key }
                    callbacks.post(result) { view ->
                        if (context.views.hasAncestorResourceName(view, "hw_device_viewpager")) {
                            if (context.config[key] == true) ViewTrimmer.collapse(view)
                            else ViewTrimmer.restore(view)
                        }
                    }
                }
                result
            }
        return 1
    }

    private fun applyNewParent(root: View, context: HookContext) {
        val packageName = context.application.packageName
        context.views.applyByResourceNames(root, packageName,
            DeviceContentKeyResolver.newParentMappings, context.config)
        val device = context.views.findByResourceName(root, packageName, "switch_device") ?: return
        val store = context.views.findByResourceName(root, packageName, "switch_web") ?: return
        val switcher = context.views.findByResourceName(root, packageName, "switch_device_layout") ?: return
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
        val content = context.views.findByResourceName(root, context.application.packageName,
            "device_scrollview_content") as? ViewGroup ?: return
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            val key = synchronized(delegateRoots) { delegateRoots[child] } ?: arkuiKeyByResource(child, context) ?: continue
            if (context.config[key] == true) ViewTrimmer.collapse(child) else ViewTrimmer.restore(child)
            if (key == SettingsKeys.DEVICE_PRIMARY) applyAddDevice(child, context)
            if (key == SettingsKeys.DEVICE_TIPS) applyTipRows(child, context)
        }
    }

    private fun arkuiKeyByResource(root: View, context: HookContext): String? =
        mapOf(
            "rl_tab_device" to SettingsKeys.DEVICE_PRIMARY,
            "tab_all_device_layout" to SettingsKeys.DEVICE_LIST,
            "tab_setting_benefit_layout" to SettingsKeys.DEVICE_TIPS,
            "card_mywatch_view" to SettingsKeys.DEVICE_MY_WATCH,
            "card_watchface_view" to SettingsKeys.DEVICE_WATCH_FACES,
            "device_feature_container" to SettingsKeys.DEVICE_FEATURES,
        ).entries.firstOrNull { (name, _) ->
            context.views.findByResourceName(root, context.application.packageName, name) != null
        }?.value

    private fun applyAddDevice(root: View, context: HookContext) {
        context.views.findByResourceName(root, context.application.packageName, "ll_tab_device_empty")?.let {
            if (context.config[SettingsKeys.DEVICE_ADD] == true) ViewTrimmer.collapse(it)
            else ViewTrimmer.restore(it)
        }
    }

    private fun applyTipRows(root: View, context: HookContext) {
        context.views.walk(root) { view ->
            if (context.views.resourceEntryName(view) != "tab_title") return@walk
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
