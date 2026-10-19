@file:android.annotation.SuppressLint("DiscouragedApi") // 宿主资源只能按真实 APK 核验的名称解析。

package love.nairain.huawei.hook.resolver

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView

/** 依据两版 AppSettingActivity.onCreate、hw_show_settings_app 与宿主公开控件方法核验。 */
internal class HostSettingsTargets(loader: ClassLoader, version: String?, code: Long) {
    init { require(accepts(version, code)) }
    val activity = Class.forName(ACTIVITY, false, loader).asSubclass(Activity::class.java)
    private val title = Class.forName("com.huawei.ui.commonui.titlebar.CustomTitleBar", false, loader)
    private val text = Class.forName("com.huawei.ui.commonui.healthtextview.HealthTextView", false, loader)
    private val toggle = Class.forName("com.huawei.ui.commonui.switchbutton.HealthSwitchButton", false, loader)
    private val divider = Class.forName("com.huawei.ui.commonui.divider.HealthDivider", false, loader)
    private val titleConstructor = title.getConstructor(Context::class.java, AttributeSet::class.java)
    private val textConstructor = text.asSubclass(TextView::class.java).getConstructor(Context::class.java)
    private val switchConstructor = toggle.asSubclass(CompoundButton::class.java).getConstructor(Context::class.java)
    private val dividerConstructor = divider.asSubclass(View::class.java).getConstructor(Context::class.java)
    private val titleText = title.getMethod("setTitleText", String::class.java)
    private val back = title.getMethod("setLeftButtonOnClickListener", View.OnClickListener::class.java)
    private val backVisibility = title.getMethod("setLeftButtonVisibility", Int::class.javaPrimitiveType)
    /** Context 单参构造未初始化宿主布尔字段；复用设置布局真实 XML 属性走双参构造。 */
    fun title(context: Context, label: String, onBack: () -> Unit): View {
        val layout = context.resources.getIdentifier("hw_show_settings_app", "layout", context.packageName)
        val titleId = context.resources.getIdentifier("me_appsetting_title_layout", "id", context.packageName)
        require(layout != 0 && titleId != 0)
        return context.resources.getLayout(layout).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.depth != 2 || parser.name != title.name) continue
                // 真实 APK 压缩了属性命名空间，按编译后的 android:id 属性身份核验。
                if ((0 until parser.attributeCount).none { index ->
                        parser.getAttributeNameResource(index) == android.R.attr.id && parser.getAttributeResourceValue(index, 0) == titleId
                    }) continue
                val view = titleConstructor.newInstance(context, Xml.asAttributeSet(parser)) as View
                titleText.invoke(view, label)
                backVisibility.invoke(view, View.VISIBLE)
                back.invoke(view, View.OnClickListener { onBack() })
                return@use view
            }
            error("Verified settings title unavailable")
        }
    }
    fun text(context: Context): TextView = textConstructor.newInstance(context)
    fun toggle(context: Context): CompoundButton = switchConstructor.newInstance(context)
    fun divider(context: Context): View = dividerConstructor.newInstance(context)

    /** 17.0.8.300 的 CustomTitleBar 右侧文字接口；失败只隐藏本按钮。 */
    fun restartButton(bar: View, label: String, description: String, clicked: () -> Unit): TextView {
        val id = bar.resources.getIdentifier("hwtoolbar_right_text", "id", bar.context.packageName)
        require(id != 0)
        val button = descendants(bar).filter { it.id == id }.singleOrNull() as? TextView
            ?: error("Verified right text unavailable")
        try {
            title.getMethod("setRightTextContent", String::class.java).invoke(bar, label)
            title.getMethod("setRightTextOnClickListener", View.OnClickListener::class.java)
                .invoke(bar, View.OnClickListener { clicked() })
            button.contentDescription = description
            button.isFocusable = true
            button.gravity = Gravity.CENTER
            val touchSize = (48 * bar.resources.displayMetrics.density).toInt()
            button.minWidth = maxOf(button.minWidth, touchSize)
            button.minHeight = maxOf(button.minHeight, touchSize)
            title.getMethod("setRightTextVisible", Int::class.javaPrimitiveType).invoke(bar, View.VISIBLE)
            return button
        } catch (error: Throwable) {
            button.setOnClickListener(null)
            button.visibility = View.GONE
            throw error
        }
    }

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
        fun accepts(version: String?, code: Long): Boolean = when (version) {
            "17.0.8.300" -> code == 1700008300L
            else -> false
        }
        fun descendants(root: View): List<View> = buildList {
            fun visit(view: View) {
                add(view)
                if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(root)
        }
    }
}
