@file:android.annotation.SuppressLint("DiscouragedApi") // 宿主资源只能按真实 APK 核验的名称解析。

package love.nairain.huawei.hook.resolver

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** 仅解析设置入口所需的宿主文字、分隔线及目标行，不再依赖宿主配置页控件。 */
internal class HostSettingsTargets(loader: ClassLoader, version: String?, code: Long) {
    init { require(accepts(version, code)) }
    // 设置页定位失败不能阻止首页更多入口打开模块自有窗口。
    val activity by lazy { Class.forName(ACTIVITY, false, loader).asSubclass(Activity::class.java) }
    private val textConstructor = Class.forName("com.huawei.ui.commonui.healthtextview.HealthTextView", false, loader)
        .asSubclass(TextView::class.java).getConstructor(Context::class.java)
    private val dividerConstructor = Class.forName("com.huawei.ui.commonui.divider.HealthDivider", false, loader)
        .asSubclass(View::class.java).getConstructor(Context::class.java)

    fun text(context: Context): TextView = textConstructor.newInstance(context)
    fun divider(context: Context): View = dividerConstructor.newInstance(context)

    fun container(activity: Activity): Pair<ViewGroup, View>? {
        if (!this.activity.isInstance(activity)) return null
        fun id(name: String) = activity.resources.getIdentifier(name, "id", activity.packageName)
        val parentId = id("app_setting_activity_layout")
        val goalId = id("hw_goal_layout")
        if (parentId == 0 || goalId == 0) return null
        return selectContainer(activity.window.decorView, parentId, goalId)
    }

    companion object {
        fun selectContainer(root: View, parentId: Int, goalId: Int): Pair<ViewGroup, View>? {
            if (parentId == 0 || goalId == 0 || parentId == goalId) return null
            val all = descendants(root)
            val parent = all.filter { it.id == parentId }.singleOrNull() as? ViewGroup ?: return null
            val goal = all.filter { it.id == goalId }.singleOrNull() ?: return null
            if (goal.parent !== parent || goal.visibility != View.VISIBLE || parent.visibility != View.VISIBLE) return null
            return parent to goal
        }
        const val ACTIVITY = "com.huawei.ui.main.stories.me.activity.AppSettingActivity"
        fun accepts(version: String?, code: Long): Boolean = version == "17.0.8.300" && code == 1700008300L
        fun descendants(root: View): List<View> = buildList {
            fun visit(view: View) {
                add(view)
                if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(root)
        }
    }
}
