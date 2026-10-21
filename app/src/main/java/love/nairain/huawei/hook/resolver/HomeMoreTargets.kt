package love.nairain.huawei.hook.resolver

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import love.nairain.huawei.scan.HomeMoreDiscovery
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

/** 默认常驻的备用入口，使用独立 DexKit 证据；歧义结构不挂载。 */
internal class HomeMoreTargets(loader: ClassLoader, private val binding: HomeMoreDiscovery.Binding) {
    val fragment = Class.forName(FRAGMENT, false, loader)
    private val title = Class.forName("com.huawei.ui.commonui.titlebar.CustomTitleBar", false, loader)
    val createView = DexMethod(binding.create).getMethodInstance(loader)
        .also { require(it.returnType == View::class.java) }
    val bind = DexMethod(binding.bind).getMethodInstance(loader)
    private val titleField = DexField(binding.titleField).getFieldInstance(loader).apply { isAccessible = true }
    private val clickField = DexField(binding.clickField).getFieldInstance(loader).apply { isAccessible = true }
    private val getActivity = fragment.getMethod("getActivity")
    private val getView = fragment.getMethod("getView")
    fun activity(instance: Any): Activity? = getActivity.invoke(instance) as? Activity
    fun root(instance: Any): View? = getView.invoke(instance) as? View
    fun button(root: View): View? {
        val (bar, button, icon) = binding.ids
        return selectButton(root, bar, button, icon)?.takeIf { selected ->
            val current = HostSettingsTargets.descendants(root).singleOrNull { view -> view.id == bar }
            current != null && title.isInstance(current) && clickField.get(current) === selected
        }
    }
    fun ownsTitle(instance: Any, root: View): Boolean {
        val title = titleField.get(instance) ?: return false
        // 只检查页面归属；按钮可能尚未显示，仍需注册观察器等待后续布局。
        return HostSettingsTargets.descendants(root).singleOrNull { it.id == binding.ids.first() } === title
    }
    companion object {
        const val FRAGMENT = HomeMoreDiscovery.FRAGMENT
        fun selectButton(root: View, barId: Int, buttonId: Int, iconId: Int): View? {
            if (setOf(barId, buttonId, iconId).size != 3 || listOf(barId, buttonId, iconId).any { it == 0 }) return null
            val all = HostSettingsTargets.descendants(root)
            val bar = all.filter { it.id == barId }.singleOrNull() as? ViewGroup ?: return null
            val children = HostSettingsTargets.descendants(bar)
            val button = all.filter { it.id == buttonId }.singleOrNull() as? LinearLayout ?: return null
            val icon = all.filter { it.id == iconId }.singleOrNull() as? ImageView ?: return null
            if (button !in children || icon.parent !== button || !button.isClickable || !button.isEnabled) return null
            var current: View? = icon
            while (current != null) {
                if (current.visibility != View.VISIBLE) return null
                if (current === root) return button
                current = current.parent as? View
            }
            return null
        }
    }
}
