package love.nairain.huawei.scan

import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys as K
import love.nairain.huawei.hook.resolver.DeviceContentKeyResolver
import love.nairain.huawei.hook.resolver.RowKeyResolver
import love.nairain.huawei.hook.resolver.BottomTabKeyResolver
import love.nairain.huawei.hook.symbols.HuaweiHealthHookPoints
import org.json.JSONObject
import org.json.JSONArray
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.result.ClassData

data class LayoutResolution(
    val groups: Set<String>,
    val matched: Set<String>,
    val aliases: Map<String, String>,
    val descriptors: Set<String>,
    val mineManager: String,
    val failures: Map<String, String> = emptyMap(),
    val capabilities: Map<String, Set<String>> = emptyMap(),
) {
    fun encode(): String = JSONObject().apply {
        put("groups", JSONArray(groups.sorted())); put("matched", JSONArray(matched.sorted()))
        put("aliases", JSONObject(aliases)); put("descriptors", JSONArray(descriptors.sorted()))
        put("mine", mineManager)
        put("failures", JSONObject(failures))
        put("capabilities", JSONObject().apply { capabilities.forEach { (id, keys) -> put(id, JSONArray(keys.sorted())) } })
    }.toString()

    companion object {
        fun decode(raw: String): LayoutResolution {
            val j = JSONObject(raw)
            fun strings(key: String): Set<String> = j.getJSONArray(key).let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            }
            val a = j.getJSONObject("aliases")
            val f = j.getJSONObject("failures")
            val c = j.getJSONObject("capabilities")
            val capabilities = c.keys().asSequence().associateWith { id ->
                val keys = c.getJSONArray(id)
                (0 until keys.length()).map { keys.getString(it) }.toSet()
            }
            return LayoutResolution(strings("groups"), strings("matched"), a.keys().asSequence().associateWith { a.getString(it) },
                strings("descriptors"), j.getString("mine"), f.keys().asSequence().associateWith { f.getString(it) }, capabilities)
                .also {
                    require(ScanProtocol.keys.containsAll(it.matched + it.failures.keys))
                    require(it.groups.containsAll(capabilities.keys))
                    require(capabilities.values.all { keys -> it.matched.containsAll(keys) })
                }
        }
    }
}

/** 只接受唯一候选；没有完整业务证据的版本后备不会进入匹配统计。 */
internal class LayoutScanner(
    private val bridges: List<DexKitBridge>,
    private val resourceId: (String, String) -> Int,
    private val verifySymbol: (String) -> Unit,
    private val known: Boolean,
) {
    private val points = HuaweiHealthHookPoints.V17_0_7_310
    private val aliases = linkedMapOf<String, String>()
    private val descriptors = linkedSetOf<String>()
    private val groups = linkedSetOf<String>()
    private val matched = linkedSetOf<String>()
    private val checked = linkedSetOf<String>()
    private var mine = points.mineListManager
    private val failures = linkedMapOf<String, String>()
    private val capabilities = linkedMapOf<String, MutableSet<String>>()
    private val types = mutableMapOf<String, ClassData>()

    private fun hierarchy(owner: String): List<ClassData> = generateSequence(type(owner)) {
        it.superClass?.let { parent -> bridges.firstNotNullOfOrNull { bridge -> bridge.getClassData(parent.name) } }
    }.take(32).toList()

    private fun remember(data: MethodData, owner: String?, old: String): MethodData {
        verifySymbol(data.descriptor)
        descriptors += data.descriptor
        aliases["${owner ?: data.className}#$old#${data.paramTypeNames.size}"] = data.name
        return data
    }

    private fun method(owner: String?, old: String, result: String, params: List<String>, anchor: String? = null): MethodData {
        val owners: List<String?> = owner?.let { hierarchy(it).map { c -> c.name } } ?: listOf(null)
        for (declaring in owners) {
        val candidates = bridges.flatMap { bridge ->
            bridge.findMethod {
                matcher {
                    if (declaring != null) declaredClass(declaring)
                    if (anchor != null && anchor != "*") usingEqStrings(listOf(anchor)) else if (anchor == null) name(old)
                    returnType(result)
                    paramTypes(*params.toTypedArray())
                }
            }
        }.distinctBy { it.descriptor }
        if (candidates.isEmpty()) continue
        require(candidates.size == 1) { "ambiguous" }
        return remember(candidates.single(), owner, old)
        }
        throw IllegalArgumentException("missing")
    }

    private fun type(name: String): ClassData = types.getOrPut(name) {
        val candidates = bridges.mapNotNull { it.getClassData(name) }.distinctBy { it.name }
        require(candidates.size == 1) { "class_missing" }
        candidates.single()
    }.also { if (it.descriptor !in descriptors) { verifySymbol(it.descriptor); descriptors += it.descriptor } }

    private fun field(owner: String, name: String, expected: String) {
        val data = type(owner).fields.filter { it.name == name && it.typeName == expected }.singleOrNull()
            ?: throw IllegalArgumentException("field_missing")
        verifySymbol(data.descriptor)
        descriptors += data.descriptor
    }

    private fun usedField(method: MethodData) {
        val data = method.usingFields.map { it.field }.filter { it.className == method.className && it.typeName == "java.util.List" }
            .distinctBy { it.descriptor }.singleOrNull() ?: throw IllegalArgumentException("field_ambiguous")
        verifySymbol(data.descriptor)
        aliases["${method.className}#k#field"] = data.name
        descriptors += data.descriptor
    }

    private fun lifecycle(owner: String) {
        type(owner)
        method(owner, "onCreateView", "android.view.View", listOf("android.view.LayoutInflater", "android.view.ViewGroup", "android.os.Bundle"))
        val resume = hierarchy(owner).firstNotNullOfOrNull { c -> c.methods.singleOrNull {
            it.name == "onResume" && it.returnTypeName == "void" && it.paramTypeNames.isEmpty()
        } }
        if (resume != null) remember(resume, owner, "onResume")
    }

    private fun resource(name: String): Boolean = listOf("id", "string").any {
        resourceId(name, it) != 0
    }

    private fun contentResource(names: Map<String, String>, key: String): Boolean {
        val found = names.filterValues { it == key }.keys.mapNotNull { name ->
            resourceId(name, "string").takeIf { it != 0 }?.let { id -> name to id }
        }
        found.forEach { (_, id) ->
            require(aliases["content:$id"].let { it == null || it == key }) { "ambiguous" }
            aliases["content:$id"] = key
        }
        return found.isNotEmpty()
    }

    private fun group(id: String, keys: Set<String>, verify: () -> Unit) {
        val previousAliases = aliases.toMap()
        val previousDescriptors = descriptors.toSet()
        try {
            verify()
            groups += id
            matched += keys
            capabilities.getOrPut(id) { linkedSetOf() }.addAll(keys)
        } catch (error: Throwable) {
            if (error !is IllegalArgumentException && error !is ReflectiveOperationException && error !is NoSuchElementException &&
                (error !is LinkageError || error is UnsatisfiedLinkError)) throw error
            aliases.clear(); aliases.putAll(previousAliases)
            descriptors.clear(); descriptors.addAll(previousDescriptors)
            keys.forEach { failures[it] = when {
                error is LinkageError -> "error"
                error.message == "ambiguous" || error.message == "field_ambiguous" -> "ambiguous"
                else -> "unresolved"
            } }
        }
        checked += keys
    }

    fun scan(progress: (Set<String>, Set<String>) -> Unit): LayoutResolution {
        fun run(id: String, keys: Set<String>, verify: () -> Unit) { group(id, keys, verify); progress(checked.toSet(), matched.toSet()) }
        fun category(c: SettingsCategory) = SettingsCatalog.settingsFor(c).map { it.key }.toSet()
        run("health.top", setOf(K.HEALTH_SEARCH, K.HEALTH_MORE)) {
            lifecycle(points.homeFragment)
            require(resource("health_tab_titlebar"))
            val title = resourceId("health_tab_titlebar", "id")
            require(title != 0)
            method("com.huawei.ui.commonui.titlebar.CustomTitleBar", "setRightSoftkeyVisibility", "void", listOf("int"))
            method("com.huawei.ui.commonui.titlebar.CustomTitleBar", "setRightButtonVisibility", "void", listOf("int"))
        }
        val top = setOf(
            K.HEALTH_ACTIVITY_RINGS,
            K.HEALTH_QUICK_ENTRIES,
            K.HEALTH_TODAY,
            K.HEALTH_INSIGHTS,
            K.HEALTH_HEADLINES,
            K.HEALTH_TIPS,
        )
        run("health.top-cards", top) {
            val adapter = type(points.homeAdapter)
            val constructor = adapter.methods.filter { it.name == "<init>" && it.paramTypeNames == listOf("android.content.Context", "java.util.List") }.singleOrNull()
                ?: throw IllegalArgumentException("constructor_missing")
            remember(constructor, adapter.name, "<init>")
            val refresh = bridges.flatMap { bridge -> bridge.findMethod { matcher {
                declaredClass(points.homeAdapter); returnType("void"); paramTypes("java.util.ArrayList")
            } } }.distinctBy { it.descriptor }.singleOrNull() ?: throw IllegalArgumentException()
            verifySymbol(refresh.descriptor)
            require(refresh.usingFields.isNotEmpty())
            aliases["${points.homeAdapter}#c#1"] = refresh.name
            descriptors += refresh.descriptor
            listOf(
                "SCUI_TwoModelCardData",
                "OperationCardData",
                "HealthInsightsCardData",
                "HealthHeadLinesCardData",
                "OperaMsgCardData",
                "FunctionMenuCardData",
            ).forEach {
                method(null, "getCardName", "java.lang.String", emptyList(), it)
            }
        }
        val cards = category(SettingsCategory.HEALTH).filter { it.startsWith("hide.health.card.") }.toSet()
        run("health.health-cards", cards) {
            val init = method(points.functionSetHolder, "g", "void", emptyList(), "initCard mViewAdapter or cardConstructors is null")
            usedField(init)
            method(points.functionSetHolder, "c", "void", listOf("java.util.List"), "*")
            method("com.huawei.health.health.utils.functionsetcard.manager.constructor.CardConstructor", "getCardId", "java.lang.String", emptyList())
            method("com.huawei.health.health.utils.functionsetcard.reader.FunctionSetSubCardData", "getCardId", "java.lang.String", emptyList())
        }
        run("health.edit-cards", setOf(K.HEALTH_EDIT_CARDS)) {
            require(known)
            method(points.functionSetHolder, "l", "void", emptyList())
            field(points.functionSetHolder, "m", "android.widget.LinearLayout")
        }
        val sportTop = mapOf(K.SPORT_CATEGORY_BAR to "track_sport_tab", K.SPORT_SEARCH to "sport_search_icon",
            K.SPORT_MORE to "more_and_red_point", K.SPORT_BANNER to "view_sport_banner_root")
        sportTop.forEach { (key, name) -> run("sport.top", setOf(key)) { lifecycle(points.sportFragment); require(resource(name)) } }
        val otherSport = category(SettingsCategory.SPORT) - sportTop.keys
        otherSport.forEach { key -> run("sport.sections", setOf(key)) {
            type(points.sportTrigger)
            require(known || key !in setOf(K.SPORT_TRADITIONAL, K.SPORT_ENJOY, K.SPORT_TODAY, K.SPORT_MORE_COURSES))
            method(points.sportTrigger, "setCacheBeansList", "void", listOf("java.util.List"))
            method(points.sportTrigger, "getResPosId", "int", emptyList())
            require(bridges.any { bridge -> bridge.findMethod { matcher { declaredClass(points.sportFragment); usingNumbers(4040) } }.isNotEmpty() })
            val section = "com.huawei.health.knit.section.model.SectionBean"
            method(section, "n", "com.huawei.health.marketing.datatype.ResourceBriefInfo", emptyList(), "*")
            method(section, "c", "com.huawei.health.knit.data.KnitDataProvider", emptyList(), "*")
            method(section, "q", "android.view.View", emptyList(), "*")
            method("com.huawei.health.marketing.datatype.ResourceBriefInfo", "getResourceId", "java.lang.String", emptyList())
            method("com.huawei.health.marketing.datatype.ResourceBriefInfo", "getResourceName", "java.lang.String", emptyList())
        } }
        // 快捷入口绑定是列表复用恢复的独立入口，目前文字后备仅限已核验版本。
        if (known) {
            group("sport.quick-entry-bind", emptySet()) {
                // k/x 共用日志和签名，按内容访问器返回类型排除另一种网格布局。
                val candidates = bridges.flatMap { bridge -> bridge.findMethod {
                    matcher {
                        declaredClass(points.sportColumnAdapter)
                        returnType("void")
                        paramTypes("${points.sportColumnAdapter}\$e", "int")
                        usingEqStrings(listOf("setQuickEntryLayout content is null."))
                    }
                } }.distinctBy { it.descriptor }.filter { candidate ->
                    candidate.invokes.any { it.returnTypeName == "com.huawei.health.marketing.datatype.SingleEntryContent" }
                }
                require(candidates.size == 1) { "ambiguous" }
                remember(candidates.single(), points.sportColumnAdapter, "x")
                field("${points.sportColumnAdapter}\$e", "cn", "android.widget.RelativeLayout")
            }
        }
        category(SettingsCategory.DEVICE).forEach { key -> run("device.fragment.0", setOf(key)) {
            points.deviceFragments.forEach(::lifecycle)
            require(DeviceContentKeyResolver.resourceMappings.filterValues { it == key }.keys.any(::resource))
            groups += "device.fragment.1"
        } }
        if (known) {
            group("device.refresh.0", emptySet()) {
                method(points.deviceFragments[0], "a", "void", listOf("java.util.List"), "mContext = null or mMarketingBanner = null")
            }
            group("device.refresh.1", emptySet()) {
                method(points.deviceFragments[1], "b", "void", listOf("com.huawei.health.marketing.api.MarketingApi", "java.util.Map"))
            }
        }
        val headers = mapOf(K.MINE_MESSAGES to "rl_actionbar_right", K.MINE_ACCOUNT to "head_layout", K.MINE_VIP to "vip_layout")
        headers.forEach { (key, name) -> run("mine.header", setOf(key)) { lifecycle(points.mineFragment); require(resource(name)) } }
        val grid = setOf(K.MINE_GROUP, K.MINE_FAMILY, K.MINE_ANNUAL_GOAL, K.MINE_REPORTS)
        val gridNames = mapOf(K.MINE_GROUP to "MyGroupCardData", K.MINE_FAMILY to "MyFamilyHealthCardData",
            K.MINE_ANNUAL_GOAL to "AnnualFlagCardData", K.MINE_REPORTS to "MyReportCardData")
        gridNames.forEach { (key, cardName) -> run("mine.grid", setOf(key)) {
            val constructor = type(points.mineGridAdapter).methods.singleOrNull {
                it.name == "<init>" && it.paramTypeNames == listOf("android.content.Context", "java.util.List")
            } ?: throw IllegalArgumentException("constructor_missing")
            remember(constructor, points.mineGridAdapter, "<init>")
            method(points.mineGridAdapter, "c", "void", listOf("java.util.List"), "*")
            val model = method(null, "getCardName", "java.lang.String", emptyList(), cardName)
            aliases["grid:${model.className}"] = key
        } }
        run("mine.marketing", setOf(K.MINE_MARKETING)) {
            val callback = points.mineMarketingCallback
            // JADX 将该方法还原为 onSuccess(Map)，实际 Dex 中是 d(Map)，并由 onSuccess(Object) 桥接。
            val success = method(callback, "d", "void", listOf("java.util.Map"))
            require(success.invokes.any {
                it.name == "filterMarketingRules" &&
                    it.returnTypeName == "java.util.Map" &&
                    it.paramTypeNames == listOf("java.util.Map")
            })
        }
        (category(SettingsCategory.MINE) - headers.keys - grid - setOf(K.MINE_MARKETING)).forEach { key -> run("mine.rows", setOf(key)) {
            val domestic = method(null, "t", "java.util.List", emptyList(), "initRecyclerList")
            val overseas = method(domestic.className, "l", "java.util.List", emptyList(), "initOverseaRecyclerList")
            require(domestic.className == overseas.className)
            mine = domestic.className
            val adapter = "com.huawei.ui.main.stories.userprofile.activity.PersonalCenterRecyclerViewAdapter"
            val itemType = method(adapter, "getItemViewType", "int", listOf("int"))
            val divider = itemType.invokes.filter { it.returnTypeName == "int" && it.paramTypeNames.isEmpty() }
                .distinctBy { it.descriptor }.singleOrNull() ?: throw IllegalArgumentException("ambiguous")
            remember(divider, divider.className, "j")
            val bind = method(adapter, "onBindViewHolder", "void", listOf("androidx.recyclerview.widget.RecyclerView\$ViewHolder", "int"))
            val title = bind.invokes.filter { it.className == divider.className && it.returnTypeName == "int" && it.paramTypeNames.isEmpty() }
                .distinctBy { it.descriptor }.singleOrNull() ?: throw IllegalArgumentException("ambiguous")
            remember(title, title.className, "a")
            require(contentResource(RowKeyResolver.RESOURCE_NAMES, key) || known)
        } }
        category(SettingsCategory.BOTTOM).forEach { key -> run("bottom.tabs", setOf(key)) {
            type(points.bottomView)
            val clears = hierarchy(points.bottomBase).flatMap { it.methods }.filter { it.returnTypeName == "void" && it.paramTypeNames.isEmpty() &&
                it.invokes.any { call -> call.name == "removeAllViews" && call.paramTypeNames.isEmpty() } }
                .distinctBy { it.descriptor }
            require(clears.size == 1) { "ambiguous" }
            remember(clears.single(), points.bottomBase, "a")
            method(points.bottomBase, "a", "boolean", listOf("int", "android.graphics.drawable.Drawable", "boolean"), "*")
            method(points.bottomBase, "onLayout", "void", listOf("boolean", "int", "int", "int", "int"))
            method(points.bottomBase, "setSelectItemEnabled", "void", listOf("int", "boolean"))
            method("com.huawei.uikit.hwbottomnavigationview.widget.HwBottomNavigationView\$BottomNavigationItemView", "getItemIndex", "int", emptyList())
            require(contentResource(BottomTabKeyResolver.RESOURCE_NAMES, key) || known)
        } }
        if ("device.fragment.1" in groups) capabilities["device.fragment.1"] = capabilities["device.fragment.0"].orEmpty().toMutableSet()
        points.deviceFragments.indices.forEach { index ->
            if ("device.refresh.$index" in groups) {
                capabilities["device.refresh.$index"] = capabilities["device.fragment.$index"].orEmpty().toMutableSet()
            }
        }
        if ("sport.quick-entry-bind" in groups) capabilities["sport.quick-entry-bind"] = matched.filter { it.startsWith("hide.sport.entry.") }.toMutableSet()
        return LayoutResolution(groups, matched, aliases, descriptors, mine, failures, capabilities)
    }
}
