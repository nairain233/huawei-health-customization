@file:android.annotation.SuppressLint("DiscouragedApi") // 名称来自 17.0.8.300 真实布局及 CustomTitleBar 点击绑定。

package love.nairain.huawei.hook.resolver

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout

/** 默认常驻的备用入口，独立于布局扫描；未知版本或歧义结构不挂载。 */
internal class HomeMoreTargets(loader: ClassLoader) {
    val fragment = Class.forName(FRAGMENT, false, loader)
    private val title = Class.forName("com.huawei.ui.commonui.titlebar.CustomTitleBar", false, loader)
    val createView = fragment.getDeclaredMethod("onCreateView", LayoutInflater::class.java, ViewGroup::class.java, Bundle::class.java)
        .also { require(it.returnType == View::class.java) }
    private val getActivity = fragment.getMethod("getActivity")
    private val getView = fragment.getMethod("getView")
    fun activity(instance: Any): Activity? = getActivity.invoke(instance) as? Activity
    fun root(instance: Any): View? = getView.invoke(instance) as? View
    fun button(root: View): View? {
        fun id(name: String) = root.resources.getIdentifier(name, "id", root.context.packageName)
        val bar = id("health_tab_titlebar")
        val button = id("hwappbarpattern_layout_ok_icon")
        val icon = id("hwappbarpattern_ok_icon")
        return selectButton(root, bar, button, icon)?.takeIf {
            HostSettingsTargets.descendants(root).singleOrNull { view -> view.id == bar }?.let(title::isInstance) == true }
    }
    companion object {
        const val FRAGMENT = "com.huawei.ui.homehealth.HomeFragment"
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
