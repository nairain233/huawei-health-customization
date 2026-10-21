package love.nairain.huawei.scan

import java.lang.reflect.Modifier
import org.luckypray.dexkit.DexKitBridge

/** 独立入口扫描，不依赖布局开关、布局缓存或版本白名单；缺失和歧义均保持原行为。 */
internal object HomeMoreDiscovery {
    const val FRAGMENT = "com.huawei.ui.homehealth.HomeFragment"
    private const val TITLE = "com.huawei.ui.commonui.titlebar.CustomTitleBar"
    private const val LISTENER = "android.view.View\$OnClickListener"
    val resourceNames = listOf("health_tab_titlebar", "hwappbarpattern_layout_ok_icon", "hwappbarpattern_ok_icon")

    data class Binding(val create: String, val bind: String, val titleField: String, val clickField: String, val ids: List<Int>)

    fun resolve(bridges: List<DexKitBridge>, resourceId: (String, String) -> Int): Binding {
        val home = bridges.mapNotNull { it.getClassData(FRAGMENT) }.single()
        val title = bridges.mapNotNull { it.getClassData(TITLE) }.single()
        val setter = title.methods.single { it.name == "setRightButtonOnClickListener" &&
            it.returnTypeName == "void" && it.paramTypeNames == listOf(LISTENER) && !Modifier.isStatic(it.modifiers) }
        require(setter.invokes.any { it.name == "setOnClickListener" && it.paramTypeNames == listOf(LISTENER) })
        val bind = home.methods.single { method -> method.invokes.any { it.descriptor == setter.descriptor } }
        require(bind.returnTypeName == "void" && bind.paramTypeNames.isEmpty() && !Modifier.isStatic(bind.modifiers))
        // 已有宿主长按时不接管；短按始终由宿主处理。
        require(home.methods.none { method -> method.invokes.any { it.name == "setOnLongClickListener" } })
        val field = bind.usingFields.map { it.field }.distinctBy { it.descriptor }.single {
            it.className == FRAGMENT && it.typeName == TITLE && !Modifier.isStatic(it.modifiers) }
        val clickField = setter.usingFields.map { it.field }.distinctBy { it.descriptor }.single {
            it.className == TITLE && it.typeName == "android.widget.LinearLayout" && !Modifier.isStatic(it.modifiers) }
        val create = home.methods.single { it.name == "onCreateView" && it.returnTypeName == "android.view.View" &&
            it.paramTypeNames == listOf("android.view.LayoutInflater", "android.view.ViewGroup", "android.os.Bundle") &&
            !Modifier.isStatic(it.modifiers) }
        val ids = resourceNames.map { resourceId(it, "id") }
        require(ids.all { it > 0 } && ids.toSet().size == ids.size)
        return Binding(create.descriptor, bind.descriptor, field.descriptor, clickField.descriptor, ids)
    }
}
