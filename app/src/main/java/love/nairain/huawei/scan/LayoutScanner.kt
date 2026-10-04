package love.nairain.huawei.scan

import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys as K
import love.nairain.huawei.hook.resolver.DeviceContentKeyResolver
import love.nairain.huawei.hook.resolver.SportPageTargets
import love.nairain.huawei.hook.resolver.RowKeyResolver
import love.nairain.huawei.hook.resolver.BottomTabKeyResolver
import love.nairain.huawei.hook.symbols.HuaweiHealthHookPoints
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.result.ClassData

/** 按页面和业务关系定位；除明确的模型/委托集合外只接受唯一候选。 */
internal class LayoutScanner(
    private val bridges: List<DexKitBridge>,
    private val resourceId: (String, String) -> Int,
    private val verifySymbol: (String) -> Unit,
) {
    private val points = HuaweiHealthHookPoints.ANCHORS
    private val bindings = linkedMapOf<String, String>()
    private val descriptors = linkedSetOf<String>()
    private val groups = linkedSetOf<String>()
    private val matched = linkedSetOf<String>()
    private val checked = linkedSetOf<String>()
    private var mine = points.mineListManager
    private val failures = linkedMapOf<String, String>()
    private val capabilities = linkedMapOf<String, MutableSet<String>>()
    private val types = mutableMapOf<String, ClassData>()
    private val identities = linkedMapOf<String, String>()
    private val resources = linkedMapOf<String, Int>()
    private val requirements = linkedMapOf<String, MutableSet<String>>()
    private val issues = linkedMapOf<String, String>()
    private var evidence: MutableSet<String>? = null
    private val queries = mutableMapOf<String, List<MethodData>>()
    private val hierarchies = mutableMapOf<String, List<ClassData>>()

    private fun id(name: String, kind: String): Int = resources.getOrPut("$kind/$name") {
        resourceId(name, kind)
    }.also { if (it != 0) evidence?.add("resource:$kind/$name") }

    private fun descendants(owner: String, base: String): Boolean = hierarchy(owner).any { it.name == base }

    private val delegateBase = "com.huawei.ui.homehealth.devicearkui.delegate.DeviceViewDelegate"
    private val delegateEnum = "com.huawei.ui.homehealth.devicearkui.DeviceDelegateEnum"

    private fun resolveDelegates(key: String): List<String> {
        val enum = type(delegateEnum)
        val initializer = unique(enum.methods.filter { it.name == "<clinit>" })
        val factories = initializer.invokes.filter { it.name == "<init>" }.mapNotNull { constructor ->
            bridges.firstNotNullOfOrNull { it.getClassData(constructor.className) }
        }.filter { factory -> factory.interfaces.any { it.name == "$delegateEnum\$DeviceViewDelegateCreate" } }
            .distinctBy { it.name }
        val entries = factories.mapNotNull { factory ->
            val create = factory.methods.singleOrNull { it.returnTypeName == delegateBase &&
                it.paramTypeNames == listOf("androidx.fragment.app.FragmentActivity") } ?: return@mapNotNull null
            val target = create.invokes.filter { it.name == "<init>" }.singleOrNull() ?: return@mapNotNull null
            val data = bridges.firstNotNullOfOrNull { it.getClassData(target.className) } ?: return@mapNotNull null
            Triple(data, create, target)
        }
        val logs = when (key) {
            K.DEVICE_PRIMARY -> setOf("DeviceDisplayDelegate", "R_DeviceDisplayDelegate")
            K.DEVICE_LIST -> setOf("DeviceListDelegate")
            K.DEVICE_TIPS -> setOf("DeviceTipDelegate")
            K.DEVICE_MY_WATCH -> setOf("DeviceMyWatchDelegate", "DeviceCombinedWatchFaceDelegate")
            K.DEVICE_WATCH_FACES -> setOf("DeviceWatchFaceDelegate")
            else -> emptySet()
        }
        val layout = when (key) {
            K.DEVICE_FUNCTIONS -> "delegate_device_functional"
            K.DEVICE_FEATURES -> "delegate_device_feature"
            else -> null
        }
        val layoutId = layout?.let { id(it, "layout") }
        val selected = entries.filter { (data, _, _) ->
            if (logs.isNotEmpty()) data.methods.any { method -> method.usingStrings.any(logs::contains) }
            else data.methods.any { method ->
                method.name == "createItemView" && method.returnTypeName == "android.view.View" &&
                    (method.usingFields.any { it.field.name == layout && it.field.className.endsWith("R\$layout") } ||
                        (layoutId != null && layoutId != 0 && bridges.any { bridge -> bridge.findMethod { matcher {
                            declaredClass(data.name); name(method.name); usingNumbers(layoutId)
                        } }.isNotEmpty() }))
            }
        }.distinctBy { it.first.name }
        require(selected.isNotEmpty()) { "entry_missing" }
        require(key == K.DEVICE_FUNCTIONS || selected.size == 1) { "ambiguous" }
        selected.forEach { (data, factory, constructor) ->
            require(descendants(data.name, delegateBase)) { "dependency_missing" }
            if (key in setOf(K.DEVICE_FUNCTIONS, K.DEVICE_FEATURES)) require(descendants(data.name,
                "com.huawei.ui.homehealth.devicearkui.delegate.BaseFeatureDelegate")) { "dependency_missing" }
            // <clinit> 只用于 Dex 关系证据，Java 反射不能解析它，不能放入运行时成员复核集。
            listOf(factory, constructor).forEach {
                verifySymbol(it.descriptor); descriptors += it.descriptor; evidence?.add(it.descriptor)
            }
        }
        return selected.map { it.first.name }
    }

    private fun candidates(owner: String?, result: String, params: List<String>?, anchor: String? = null): List<MethodData> {
        val key = "$owner|$result|$params|$anchor"
        return queries.getOrPut(key) {
            bridges.flatMap { bridge -> bridge.findMethod { matcher {
                if (owner != null) declaredClass(owner)
                returnType(result)
                if (params != null) paramTypes(*params.toTypedArray())
                if (anchor != null) usingEqStrings(listOf(anchor))
            } } }.distinctBy { it.descriptor }
        }
    }

    private fun unique(values: List<MethodData>): MethodData {
        require(values.isNotEmpty()) { "entry_missing" }
        require(values.size == 1) { "ambiguous" }
        return values.single()
    }


    private fun hierarchy(owner: String): List<ClassData> {
        val root = type(owner)
        return hierarchies.getOrPut(owner) {
            generateSequence(root) {
                it.superClass?.let { parent -> bridges.firstNotNullOfOrNull { bridge -> bridge.getClassData(parent.name) } }
            }.take(32).toList()
        }
    }

    private fun remember(data: MethodData, owner: String?, old: String): MethodData {
        type(data.className)
        if (owner != null && owner != data.className) type(owner)
        verifySymbol(data.descriptor)
        descriptors += data.descriptor
        val role = "${owner ?: data.className}#$old#${data.paramTypeNames.size}"
        bindings[role] = data.descriptor
        evidence?.add("binding:$role")
        evidence?.add(data.descriptor)
        return data
    }

    private fun identity(role: String, key: String) {
        require(identities[role].let { it == null || it == key }) { "ambiguous" }
        identities[role] = key
        evidence?.add("identity:$role")
    }

    private fun method(owner: String?, old: String, result: String, params: List<String>, anchor: String? = null): MethodData {
        val owners: List<String?> = owner?.let { hierarchy(it).map { c -> c.name } } ?: listOf(null)
        for (declaring in owners) {
            val candidates = candidates(declaring, result, params, anchor?.takeIf { it != "*" })
                .filter { anchor != null || it.name == old }
            if (candidates.isEmpty()) continue
            return remember(unique(candidates), owner, old)
        }
        throw IllegalArgumentException("missing")
    }

    private fun type(name: String): ClassData = types.getOrPut(name) {
        val candidates = bridges.mapNotNull { it.getClassData(name) }.distinctBy { it.name }
        require(candidates.size == 1) { "class_missing" }
        candidates.single()
    }.also {
        if (it.descriptor !in descriptors) { verifySymbol(it.descriptor); descriptors += it.descriptor }
        evidence?.add(it.descriptor)
    }

    private fun lifecycle(owner: String) {
        type(owner)
        method(owner, "onCreateView", "android.view.View", listOf("android.view.LayoutInflater", "android.view.ViewGroup", "android.os.Bundle"))
        val resume = hierarchy(owner).firstNotNullOfOrNull { c -> c.methods.singleOrNull {
            it.name == "onResume" && it.returnTypeName == "void" && it.paramTypeNames.isEmpty()
        } }
        if (resume != null) remember(resume, owner, "onResume")
        val getView = hierarchy(owner).firstNotNullOfOrNull { c -> c.methods.singleOrNull {
            it.name == "getView" && it.returnTypeName == "android.view.View" && it.paramTypeNames.isEmpty()
        } }
        require(getView != null) { "entry_missing" }
        remember(getView, owner, "getView")
    }

    private fun resource(name: String): Boolean = id(name, "id") != 0

    private fun contentResource(names: Map<String, String>, key: String): Boolean {
        val found = names.filterValues { it == key }.keys.mapNotNull { name ->
            id(name, "string").takeIf { it != 0 }?.let { id -> name to id }
        }
        found.forEach { (_, id) ->
            require(identities["content:$id"].let { it == null || it == key }) { "ambiguous" }
            identity("content:$id", key)
        }
        return found.isNotEmpty()
    }

    private fun group(id: String, keys: Set<String>, verify: () -> Unit) {
        val previousBindings = bindings.toMap()
        val previousIdentities = identities.toMap()
        val parentEvidence = evidence
        evidence = linkedSetOf()
        val previousDescriptors = descriptors.toSet()
        try {
            verify()
            requirements.getOrPut(id) { linkedSetOf() }.addAll(evidence.orEmpty())
            groups += id
            matched += keys
            capabilities.getOrPut(id) { linkedSetOf() }.addAll(keys)
        } catch (error: Throwable) {
            if (error !is IllegalArgumentException && error !is ReflectiveOperationException && error !is NoSuchElementException &&
                (error !is LinkageError || error is UnsatisfiedLinkError)) throw error
            bindings.clear(); bindings.putAll(previousBindings)
            identities.clear(); identities.putAll(previousIdentities)
            descriptors.clear(); descriptors.addAll(previousDescriptors)
            val reason = when {
                error is LinkageError -> "error"
                error is ReflectiveOperationException -> "reflection_failed"
                error.message in setOf("ambiguous", "field_ambiguous") -> "ambiguous"
                error.message in setOf("resource_missing", "dependency_missing") -> error.message!!
                else -> "entry_missing"
            }
            issues[if (keys.size == 1) "$id:${keys.single()}" else id] = reason
            keys.forEach { failures[it] = reason }
        } finally { evidence = parentEvidence }
        checked += keys
    }

    fun scan(progress: (Set<String>, Set<String>) -> Unit): LayoutResolution {
        fun run(id: String, keys: Set<String>, verify: () -> Unit) { group(id, keys, verify); progress(checked.toSet(), matched.toSet()) }
        fun category(c: SettingsCategory) = SettingsCatalog.settingsFor(c).map { it.key }.toSet()
        // 视图回调只读快照，所有允许访问的资源在工作线程一次性解析。
        val viewNames = SportPageTargets.controls.keys + DeviceContentKeyResolver.resourceMappings.keys +
            DeviceContentKeyResolver.newParentMappings.keys + setOf("health_tab_titlebar", "modify_cards_layout",
                "track_sport_tab", "item_quick_entry_root_layout", "layout_marketing_grid", "series_course_layout",
                "section_root_view", "item_two_landscape_layout", "item_two_landscape_title", "plan_resource_slot",
                "normal_view_fitness", "common_card_button_fitness", "sport_viewPager_container", "device_card",
                "switch_device_layout", "switch_device", "switch_web", "hw_device_viewpager", "device_scrollview_content",
                "rl_tab_device", "ll_tab_device_empty", "tab_all_device_layout", "tab_setting_benefit_layout",
                "tab_title", "card_mywatch_view", "card_mywatch_custom_view", "card_watchface_view", "device_feature_container",
                "rl_actionbar_right", "rl_message_new", "head_layout", "account_center_customheadview", "vip_layout")
        viewNames.forEach { id(it, "id") }
        setOf(K.HEALTH_SEARCH, K.HEALTH_MORE).forEach { key -> run("health.top", setOf(key)) {
            lifecycle(points.homeFragment)
            require(id("health_tab_titlebar", "id") != 0) { "resource_missing" }
            method("com.huawei.ui.commonui.titlebar.CustomTitleBar",
                if (key == K.HEALTH_SEARCH) "setRightSoftkeyVisibility" else "setRightButtonVisibility",
                "void", listOf("int"))
        } }
        val cardKeys = mapOf(
            "SCUI_TwoModelCardData" to K.HEALTH_ACTIVITY_RINGS,
            "OperationCardData" to K.HEALTH_TODAY,
            "HealthInsightsCardData" to K.HEALTH_INSIGHTS,
            "HealthHeadLinesCardData" to K.HEALTH_HEADLINES,
            "OperaMsgCardData" to K.HEALTH_TIPS,
            "FunctionMenuCardData" to K.HEALTH_QUICK_ENTRIES,
            "HealthQuickEntryCardData" to K.HEALTH_QUICK_ENTRIES,
        )
        cardKeys.forEach { (cardName, key) -> run("health.top-cards", setOf(key)) {
            val adapter = type(points.homeAdapter)
            val constructor = unique(adapter.methods.filter { it.name == "<init>" &&
                it.paramTypeNames == listOf("android.content.Context", "java.util.List") })
            remember(constructor, adapter.name, "<init>")
            val refresh = unique(candidates(adapter.name, "void", listOf("java.util.ArrayList")))
            require(refresh.usingFields.isNotEmpty()) { "dependency_missing" }
            remember(refresh, adapter.name, "cards.refresh")
            val models = candidates(null, "java.lang.String", emptyList(), cardName).filter { it.name == "getCardName" }
            require(models.isNotEmpty()) { "entry_missing" }
            if (cardName == "SCUI_TwoModelCardData") {
                // 多模型身份必须由真实首页创建关系和卡片继承共同证明，不固定混淆类名。
                val created = type(points.homeFragment).methods.flatMap { it.invokes }
                    .filter { it.name == "<init>" }.map { it.className }.toSet()
                require(models.all { it.className in created && descendants(it.className,
                    "com.huawei.health.health.utils.functionsetcard.AbstractBaseCardData") }) { "ambiguous" }
            } else require(models.size == 1) { "ambiguous" }
            models.forEach { model ->
                require(descendants(model.className,
                    "com.huawei.health.health.utils.functionsetcard.AbstractBaseCardData")) { "dependency_missing" }
                remember(model, model.className, "card.identity")
                identity("card:${model.className}:$cardName", key)
            }
        } }
        run("health.edit-cards", setOf(K.HEALTH_EDIT_CARDS)) {
            type(points.functionSetHolder)
            val root = hierarchy(points.functionSetHolder).firstNotNullOfOrNull { level -> level.fields.singleOrNull {
                it.name == "itemView" && it.typeName == "android.view.View"
            } } ?: throw IllegalArgumentException("entry_missing")
            verifySymbol(root.descriptor); descriptors += root.descriptor; evidence?.add(root.descriptor)
            bindings["${points.functionSetHolder}#edit.root#field"] = root.descriptor
            evidence?.add("binding:${points.functionSetHolder}#edit.root#field")
            require(id("modify_cards_layout", "id") != 0) { "resource_missing" }
            val titleId = id("IDS_hw_health_home_edit_card", "string")
            require(titleId != 0) { "resource_missing" }
            val updates = candidates(points.functionSetHolder, "void", emptyList()).filter { update ->
                update.invokes.any { it.name == "setVisibility" && it.paramTypeNames == listOf("int") } &&
                    (update.usingFields.any { it.field.name == "IDS_hw_health_home_edit_card" } ||
                    bridges.any { bridge -> bridge.findMethod { matcher {
                        declaredClass(points.functionSetHolder); name(update.name); usingNumbers(titleId)
                    } }.isNotEmpty() })
            }
            remember(unique(updates), points.functionSetHolder, "edit.update")
        }
        val sportControls = SportPageTargets.controls.entries.groupBy({ it.value }, { it.key })
        sportControls.forEach { (key, names) -> run("sport.chrome", setOf(key)) {
            lifecycle(points.sportFragment)
            require(names.any(::resource)) { "resource_missing" }
            if (key in setOf(K.SPORT_RUN_SUMMARY, K.SPORT_RUN_ROUTE, K.SPORT_RUN_WARMUP, K.SPORT_RUN_BEGIN,
                K.SPORT_RUN_MUSIC, K.SPORT_YOGA_COURSES, K.SPORT_FITNESS_SUMMARY))
                require(id("track_sport_tab", "id") != 0) { "dependency_missing" }
        } }
        val quickKeys = SportPageTargets.quickEntries.values.toSet()
        (quickKeys + K.SPORT_QUICK_ENTRIES).forEach { key -> run("sport.quick-views", setOf(key)) {
            lifecycle(points.sportFragment)
            require(resource("item_quick_entry_root_layout") && resource("layout_marketing_grid"))
        } }
        SportPageTargets.sections.values.toSet().forEach { key -> run("sport.section-views", setOf(key)) {
            lifecycle(points.sportFragment)
            require(id("track_sport_tab", "id") != 0) { "dependency_missing" }
            require(if (key == K.SPORT_PLAN_WEIGHT || key == K.SPORT_PLAN_TRAINING)
                resource("item_two_landscape_layout") && resource("item_two_landscape_title")
            else listOf("layout_marketing_grid", "series_course_layout", "section_root_view").any(::resource))
        } }
        run("sport.yoga-views", setOf(K.SPORT_YOGA_SUMMARY)) {
            lifecycle(points.sportFragment)
            require(id("track_sport_tab", "id") != 0) { "dependency_missing" }
            require(resource("normal_view_fitness") && resource("common_card_button_fitness"))
        }
        run("sport.plan-views", setOf(K.SPORT_PLAN_CARDS)) {
            lifecycle(points.sportFragment)
            require(id("track_sport_tab", "id") != 0) { "dependency_missing" }
            require(resource("plan_resource_slot"))
        }
        val otherSport = setOf(K.SPORT_STRETCH, K.SPORT_TRADITIONAL, K.SPORT_CYCLING,
            K.SPORT_GOLF, K.SPORT_DANCE, K.SPORT_PILATES, K.SPORT_ENJOY, K.SPORT_TODAY,
            K.SPORT_MORE_COURSES, K.SPORT_COACHES)
        otherSport.forEach { key -> run("sport.sections", setOf(key)) {
            type(points.sportTrigger)
            method(points.sportTrigger, "setCacheBeansList", "void", listOf("java.util.List"))
            method(points.sportTrigger, "getResPosId", "int", emptyList())
            require(bridges.any { bridge -> bridge.findMethod { matcher { declaredClass(points.sportFragment); usingNumbers(4040) } }.isNotEmpty() })
            val section = "com.huawei.health.knit.section.model.SectionBean"
            method(section, "section.info", "com.huawei.health.marketing.datatype.ResourceBriefInfo", emptyList(), "*")
            method(section, "section.provider", "com.huawei.health.knit.data.KnitDataProvider", emptyList(), "*")
            method(section, "section.view", "android.view.View", emptyList(), "*")
            method("com.huawei.health.marketing.datatype.ResourceBriefInfo", "getResourceId", "java.lang.String", emptyList())
            method("com.huawei.health.marketing.datatype.ResourceBriefInfo", "getResourceName", "java.lang.String", emptyList())
        } }
        // 绑定日志存在两个同类入口；内容返回类型与实际调用的根 accessor 一起消歧。
        group("sport.quick-entry-bind", emptySet()) {
            require(id("sport_viewPager_container", "id") != 0) { "resource_missing" }
            val bind = unique(candidates(points.sportColumnAdapter, "void", null,
                "setQuickEntryLayout content is null.").filter { candidate ->
                candidate.paramTypeNames.size == 2 && candidate.paramTypeNames[1] == "int" &&
                    candidate.paramTypeNames[0].startsWith("${points.sportColumnAdapter}\$") &&
                    candidate.invokes.any { it.returnTypeName == "com.huawei.health.marketing.datatype.SingleEntryContent" }
            })
            remember(bind, points.sportColumnAdapter, "quick.bind")
            val holder = bind.paramTypeNames[0]
            type(holder)
            val fields = bind.invokes.filter { it.className == holder && it.returnTypeName == "android.widget.RelativeLayout" }
                .flatMap { it.usingFields }.map { it.field }.filter { it.className == holder && it.typeName == "android.widget.RelativeLayout" }
                .distinctBy { it.descriptor }
            require(fields.size == 1) { if (fields.isEmpty()) "entry_missing" else "ambiguous" }
            val root = fields.single()
            verifySymbol(root.descriptor); descriptors += root.descriptor; evidence?.add(root.descriptor)
            bindings["$holder#quick.root#field"] = root.descriptor
            evidence?.add("binding:$holder#quick.root#field")
            require(id("item_quick_entry_root_layout", "id") != 0) { "resource_missing" }
        }
        if ("sport.quick-entry-bind" !in groups) {
            // 没有复用恢复入口就关闭单项路径；整区隐藏仍可使用独立页面入口。
            capabilities["sport.quick-views"]?.removeAll(quickKeys)
            quickKeys.forEach { key ->
                if (capabilities.values.none { key in it }) matched.remove(key)
                failures[key] = "dependency_missing"
            }
        }
        points.deviceFragments.forEachIndexed { index, page ->
            DeviceContentKeyResolver.resourceMappings.values.toSet().forEach { key -> run("device.fragment.$index", setOf(key)) {
                lifecycle(page)
                require(DeviceContentKeyResolver.resourceMappings.filterValues { it == key }.keys.any(::resource)) { "resource_missing" }
            } }
        }
        val newParent = mapOf(
            K.DEVICE_SEARCH to "hwappbarpattern_layout_ok_icon",
            K.DEVICE_MENU to "hwappbarpattern_layout_menu_icon",
            K.DEVICE_SWITCHER to "switch_device_layout",
            K.DEVICE_TAB_DEVICE to "switch_device",
            K.DEVICE_TAB_STORE to "switch_web",
            K.DEVICE_STORE to "switch_web",
        )
        newParent.forEach { (key, name) -> run("device.new.parent", setOf(key)) {
            lifecycle(points.newDeviceFragment)
            require(resource(name)) { "resource_missing" }
            if (key in setOf(K.DEVICE_SWITCHER, K.DEVICE_TAB_DEVICE, K.DEVICE_TAB_STORE, K.DEVICE_STORE))
                require(listOf("switch_device_layout", "switch_device", "switch_web").all { id(it, "id") != 0 }) { "dependency_missing" }
        } }
        val arkuiResources = mapOf(
            K.DEVICE_PRIMARY to "rl_tab_device", K.DEVICE_ADD to "ll_tab_device_empty",
            K.DEVICE_LIST to "tab_all_device_layout", K.DEVICE_TIPS to "tab_setting_benefit_layout",
            K.DEVICE_GENERAL_SETTINGS to "tab_title", K.DEVICE_DISCONNECT_PROTECTION to "tab_title",
            K.DEVICE_MY_WATCH to "card_mywatch_view", K.DEVICE_WATCH_FACES to "card_watchface_view",
            K.DEVICE_FUNCTIONS to "device_scrollview_content", K.DEVICE_FEATURES to "device_feature_container",
        )
        arkuiResources.forEach { (key, name) -> run("device.new.arkui", setOf(key)) {
            lifecycle(points.arkuiDeviceFragment)
            require(resource("device_scrollview_content") && resource(name)) { "resource_missing" }
            require(id("hw_device_viewpager", "id") != 0) { "resource_missing" }
            method("com.huawei.ui.homehealth.devicearkui.delegate.BaseViewDelegate", "obtainView",
                "android.view.View", listOf("android.view.LayoutInflater", "android.view.ViewGroup"))
            val role = when (key) {
                K.DEVICE_ADD -> K.DEVICE_PRIMARY
                K.DEVICE_GENERAL_SETTINGS, K.DEVICE_DISCONNECT_PROTECTION -> K.DEVICE_TIPS
                else -> key
            }
            resolveDelegates(role).forEach { delegate -> identity("delegate:$delegate", role) }
        } }
        run("device.new.store", setOf(K.DEVICE_STORE)) {
            lifecycle(points.vmallFragment)
            require(resource("device_vmall_card_layout"))
        }
        group("device.refresh.0", emptySet()) {
            method(points.deviceFragments[0], "device.refresh", "void", listOf("java.util.List"), "mContext = null or mMarketingBanner = null")
        }
        group("device.refresh.1", emptySet()) {
            val refresh = unique(candidates(points.deviceFragments[1], "void",
                listOf("com.huawei.health.marketing.api.MarketingApi", "java.util.Map")).filter { update ->
                update.invokes.any { it.className == "com.huawei.health.marketing.api.MarketingApi" } &&
                    update.invokes.filter { it.className == points.deviceFragments[1] }.any { local ->
                        local.invokes.any { it.name in setOf("addView", "setVisibility", "removeAllViews") }
                    }
            })
            remember(refresh, points.deviceFragments[1], "device.refresh")
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
            method(points.mineGridAdapter, "grid.refresh", "void", listOf("java.util.List"), "*")
            val model = unique(candidates(null, "java.lang.String", emptyList(), cardName).filter { it.name == "getCardName" })
            remember(model, model.className, "grid.identity")
            identity("grid:${model.className}:$cardName", key)
        } }
        run("mine.marketing", setOf(K.MINE_MARKETING)) {
            val adapter = "com.huawei.ui.main.stories.userprofile.activity.PersonalCenterRecyclerViewAdapter"
            val callbacks = candidates(null, "void", listOf("java.util.Map")).filter { success ->
                success.className.startsWith("$adapter\$") && success.invokes.any {
                    it.className == "com.huawei.health.marketing.api.MarketingApi" && it.name == "filterMarketingRules" &&
                        it.returnTypeName == "java.util.Map" && it.paramTypeNames == listOf("java.util.Map")
                }
            }
            val success = unique(callbacks)
            require(descendants(success.className, "com.huawei.hmf.tasks.OnSuccessListener") ||
                type(success.className).interfaces.any { it.name == "com.huawei.hmf.tasks.OnSuccessListener" }) { "dependency_missing" }
            val requests = bridges.flatMap { bridge -> bridge.findMethod { matcher {
                usingNumbers(4168, 9013)
            } } }.filter { it.className.startsWith("$adapter\$") && it.invokes.any { call ->
                call.className == success.className && call.name == "<init>"
            } }
            require(requests.size == 1 && requests.single().invokes.any { it.name == "addOnSuccessListener" }) { "dependency_missing" }
            remember(success, success.className, "marketing.success")
            requests.single().let { verifySymbol(it.descriptor); descriptors += it.descriptor; evidence?.add(it.descriptor) }
            bindings["mine.marketing"] = success.descriptor
            evidence?.add("binding:mine.marketing")
        }
        (category(SettingsCategory.MINE) - headers.keys - grid - setOf(K.MINE_MARKETING)).forEach { key -> run("mine.rows", setOf(key)) {
            val domestic = method(null, "rows.domestic", "java.util.List", emptyList(), "initRecyclerList")
            val overseas = method(domestic.className, "rows.overseas", "java.util.List", emptyList(), "initOverseaRecyclerList")
            require(domestic.className == overseas.className)
            mine = domestic.className
            val adapter = "com.huawei.ui.main.stories.userprofile.activity.PersonalCenterRecyclerViewAdapter"
            val itemType = method(adapter, "getItemViewType", "int", listOf("int"))
            val divider = itemType.invokes.filter { it.returnTypeName == "int" && it.paramTypeNames.isEmpty() }
                .distinctBy { it.descriptor }.singleOrNull() ?: throw IllegalArgumentException("ambiguous")
            remember(divider, divider.className, "row.type")
            val bind = method(adapter, "onBindViewHolder", "void", listOf("androidx.recyclerview.widget.RecyclerView\$ViewHolder", "int"))
            val title = bind.invokes.filter { it.className == divider.className && it.returnTypeName == "int" && it.paramTypeNames.isEmpty() }
                .distinctBy { it.descriptor }.singleOrNull() ?: throw IllegalArgumentException("ambiguous")
            remember(title, title.className, "row.title")
            require(contentResource(RowKeyResolver.RESOURCE_NAMES, key)) { "resource_missing" }
        } }
        category(SettingsCategory.BOTTOM).forEach { key -> run("bottom.tabs", setOf(key)) {
            type(points.bottomView)
            val clears = hierarchy(points.bottomBase).flatMap { it.methods }.filter { it.returnTypeName == "void" && it.paramTypeNames.isEmpty() &&
                it.invokes.any { call -> call.name == "removeAllViews" && call.paramTypeNames.isEmpty() } }
                .distinctBy { it.descriptor }
            require(clears.size == 1) { "ambiguous" }
            remember(clears.single(), points.bottomBase, "bottom.clear")
            method(points.bottomBase, "bottom.add", "boolean", listOf("int", "android.graphics.drawable.Drawable", "boolean"), "*")
            method(points.bottomBase, "onLayout", "void", listOf("boolean", "int", "int", "int", "int"))
            method(points.bottomBase, "setSelectItemEnabled", "void", listOf("int", "boolean"))
            method("com.huawei.uikit.hwbottomnavigationview.widget.HwBottomNavigationView\$BottomNavigationItemView", "getItemIndex", "int", emptyList())
            require(contentResource(BottomTabKeyResolver.RESOURCE_NAMES, key)) { "resource_missing" }
        } }
        val sportViewKeys = capabilities.filterKeys { it in setOf("sport.chrome", "sport.quick-views",
            "sport.section-views", "sport.yoga-views", "sport.plan-views") }.values.flatten().toSet()
        if (sportViewKeys.isNotEmpty()) {
            groups += "sport.top"
            capabilities["sport.top"] = sportViewKeys.toMutableSet()
        }
        // 旧页与 Arkui 页共享部分配置键；旧页命中不能授权使用基准版混淆委托。
        val arkuiKeys = capabilities["device.new.arkui"].orEmpty().toSet()
        if (arkuiKeys.isNotEmpty()) {
            groups += "device.new.delegates"
            capabilities["device.new.delegates"] = arkuiKeys.toMutableSet()
        }
        points.deviceFragments.indices.forEach { index ->
            if ("device.refresh.$index" in groups) {
                capabilities["device.refresh.$index"] = capabilities["device.fragment.$index"].orEmpty().toMutableSet()
            }
        }
        if ("sport.quick-entry-bind" in groups) capabilities["sport.quick-entry-bind"] = matched.filter { it.startsWith("hide.sport.entry.") }.toMutableSet()
        listOf("sport.top" to "sport.", "device.new.delegates" to "device.new.arkui").forEach { (target, source) ->
            if (target in groups) requirements[target] = requirements.filterKeys { it != target && it.startsWith(source) }
                .values.flatten().toMutableSet()
        }
        points.deviceFragments.indices.forEach { index ->
            if ("device.refresh.$index" in groups) requirements.getValue("device.refresh.$index").addAll(requirements["device.fragment.$index"].orEmpty())
        }
        if ("sport.quick-entry-bind" in groups) requirements["sport.quick-views"]?.addAll(requirements["sport.quick-entry-bind"].orEmpty())
        return LayoutResolution(groups.toSet(), matched.toSet(), bindings.toMap(), descriptors.toSet(), mine,
            failures.filterKeys { it !in matched }, capabilities.mapValues { it.value.toSet() },
            resources.filterValues { it != 0 }, identities.toMap(), requirements.mapValues { it.value.toSet() }, issues.toMap())
    }
}
