package love.nairain.huawei.scan

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import love.nairain.huawei.config.SettingsKeys as K

/** 两版共用生产规则；桌面 R 字段来自各自 JADX 产物并与各自资源表核对。 */
class ApkScanTest {
    private fun resources(): Map<String, Int> {
        val publicXml = File(requireNotNull(System.getProperty("scan.resources")))
        val entries = Regex("<public type=\"([^\"]+)\" name=\"([^\"]+)\" id=\"0x([0-9a-fA-F]+)\"")
            .findAll(publicXml.readText()).map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3].toLong(16).toInt()) }.toList()
        val result = entries.associate { "${it.first}/${it.second}" to it.third }.toMutableMap()
        val types = entries.groupBy { it.third }.mapValues { it.value.map { row -> row.first }.toSet() }
        val sources = File(publicXml.toPath().parent.parent.parent.parent.toFile(), "sources")
        val fields = mutableMapOf<String, MutableSet<Int>>()
        listOf("com/huawei/ui/main/R.java", "com/huawei/ui/homehealth/R.java", "com/huawei/health/R.java").forEach { owner ->
            val source = File(sources, owner).readText()
            val classes = Regex("public static (?:final )?class (\\w+) ").findAll(source).toList()
            classes.forEachIndexed { index, match ->
                val kind = match.groupValues[1]
                if (kind !in setOf("id", "string", "layout")) return@forEachIndexed
                val body = source.substring(match.range.last + 1, classes.getOrNull(index + 1)?.range?.first ?: source.length)
                Regex("public static (?:final )?int (\\w+) = (0x[0-9a-fA-F]+|[0-9]+);").findAll(body).forEach { field ->
                    val raw = field.groupValues[2]
                    val value = if (raw.startsWith("0x")) raw.drop(2).toLong(16).toInt() else raw.toInt()
                    if (types[value] == setOf(kind)) fields.getOrPut("$kind/${field.groupValues[1]}") { linkedSetOf() }.add(value)
                }
            }
        }
        fields.forEach { (key, values) ->
            val all = values + listOfNotNull(result[key])
            result[key] = all.singleOrNull() ?: 0
        }
        return result
    }

    private fun bridge(): DexKitBridge {
        val native = System.getProperty("scan.native").orEmpty()
        assumeTrue("未配置桌面 DexKit 库", native.isNotEmpty())
        val library = File(native)
        listOf("libunwind.dll", "libc++.dll").map { File(library.parentFile, it) }.filter { it.isFile }.forEach { System.load(it.absolutePath) }
        System.load(library.absolutePath)
        return DexKitBridge.create(requireNotNull(System.getProperty("scan.apk")))
    }

    @Test fun sportNavigationAndDiscoveryHaveVerifiedMembersAndIndependentFailures() {
        bridge().use { bridge ->
            val ids = resources()
            fun scan(reject: String? = null) = LayoutScanner(listOf(bridge), { name, kind -> ids["$kind/$name"] ?: 0 }, { descriptor ->
                val exists = when {
                    '(' in descriptor -> bridge.getMethodData(descriptor) != null
                    "->" in descriptor -> bridge.getFieldData(descriptor) != null
                    else -> bridge.getClassData(descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')) != null
                }
                assertTrue("描述符必须属于当前 APK：$descriptor", exists)
                if (descriptor == reject) throw NoSuchMethodException()
            }).scan { _, _ -> }
            val result = scan()
            System.getProperty("scan.output").orEmpty().takeIf { it.isNotEmpty() }?.let { File(it).writeText(result.encode()) }
            listOf("sport.identity", "sport.navigation", "sport.discovery", "sport.quick.discovery", "sport.fixed").forEach {
                assertTrue("运动能力缺失：$it，${result.issues}", it in result.groups)
            }
            assertEquals(result, ScanCache.decode(ScanCache.encode("sport-apk", "", result), "sport-apk", "") {})
            val incomplete = result.copy(requirements = result.requirements + ("sport.identity" to
                result.requirements.getValue("sport.identity").filterNot { it.endsWith("#sport.tabs.current#field") }.toSet()))
            assertThrows(IllegalArgumentException::class.java) {
                ScanCache.decode(ScanCache.encode("sport-apk", "", incomplete), "sport-apk", "") {}
            }
            fun role(name: String) = result.bindings.entries.single { it.key.substringAfter('#').substringBefore('#') == name }.value
            val noIdentity = scan(role("sport.tab.id"))
            assertFalse("sport.identity" in noIdentity.groups)
            assertFalse("sport.navigation" in noIdentity.groups)
            assertFalse("sport.discovery" in noIdentity.groups)
            assertTrue("sport.top" in noIdentity.groups)
            val noNavigation = scan(role("sport.jump"))
            assertFalse("sport.navigation" in noNavigation.groups)
            assertTrue("sport.identity" in noNavigation.groups)
            assertTrue("sport.discovery" in noNavigation.groups)
            val noQuickDiscovery = scan(role("sport.quick.content"))
            assertFalse("sport.quick.discovery" in noQuickDiscovery.groups)
            assertTrue("sport.quick-entry-bind" in noQuickDiscovery.groups)
            assertTrue("sport.discovery" in noQuickDiscovery.groups)
            assertEquals(8, ScanProtocol.RULES)
        }
    }

    @Test fun promotionUsesWholeCardGateAndFailsIndependently() {
        bridge().use { bridge ->
            val ids = resources()
            fun scan(missingResource: String? = null, reject: (String) -> Unit = {}) =
                LayoutScanner(listOf(bridge), { name, kind ->
                    if ("$kind/$name" == missingResource) 0 else ids["$kind/$name"] ?: 0
                }, { descriptor ->
                    val exists = when {
                        '(' in descriptor -> bridge.getMethodData(descriptor) != null
                        "->" in descriptor -> bridge.getFieldData(descriptor) != null
                        else -> bridge.getClassData(descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')) != null
                    }
                    assertTrue("描述符必须属于当前 APK：$descriptor", exists)
                    reject(descriptor)
                }).scan { _, _ -> }
            val result = scan()
            System.getProperty("scan.output").orEmpty().takeIf { it.isNotEmpty() }?.let {
                File(it).writeText(result.encode())
            }
            assertEquals(setOf(K.HEALTH_PROMOTION), result.capabilities["health.promotion"])
            assertFalse(love.nairain.huawei.config.SettingsCatalog.defaults.getValue(K.HEALTH_PROMOTION))
            val gate = result.bindings.entries.single { it.key.endsWith("#promotion.visible#0") }.value
            assertTrue(gate.endsWith("()Z"))
            val owner = "com.huawei.health.functionsetcard.FunctionSetViewAdapter"
            listOf("getItemId#1", "getItemViewType#1", "promotion.count#0").forEach { role ->
                val caller = bridge.getMethodData(result.bindings.getValue("$owner#$role"))!!
                assertTrue("数量与索引必须调用同一个展示判断", caller.invokes.any { it.descriptor == gate })
            }
            val scoped = result.bindings.filterKeys { it.startsWith("$owner#promotion.scope.") }
            val callers = bridge.getClassData(owner)!!.methods.filter {
                it.invokes.any { call -> call.descriptor == gate }
            }.map { it.descriptor }.toSet()
            assertEquals("所有列表查询都必须限定展示判断的调用范围", callers, scoped.values.toSet())
            val notification = result.bindings.entries.single { it.key.contains("#promotion.notification#") }.value
            val notifyCalls = bridge.getMethodData(notification)!!.invokes.map { it.name }.toSet()
            assertTrue(notifyCalls.containsAll(listOf("notifyItemInserted", "notifyItemRemoved")))
            val missing = scan("id/function_set_view")
            val missingCard = scan("id/daily_moment_root_layout")
            val rejected = scan(reject = { if (it == gate) throw NoSuchMethodException() })
            val ambiguous = scan(reject = { if (it == gate) throw IllegalArgumentException("ambiguous") })
            val missingNotification = scan(reject = { if (it == notification) throw NoSuchMethodException() })
            listOf(missing, missingCard, rejected, ambiguous, missingNotification).forEach {
                assertFalse(K.HEALTH_PROMOTION in it.matched)
                assertTrue(K.HEALTH_TODAY in it.matched)
                assertTrue(K.HEALTH_EDIT_CARDS in it.matched)
                assertEquals(it, ScanCache.decode(ScanCache.encode("apk", "", it), "apk", "") {})
            }
            assertEquals("ambiguous", ambiguous.failures[K.HEALTH_PROMOTION])
            (scoped.keys + result.bindings.keys.filter { it.contains("#promotion.notification#") ||
                it.endsWith("#promotion.visible#0") }).forEach { role ->
                assertTrue(runCatching { LayoutResolution.decode(result.copy(bindings = result.bindings - role).encode()) }.isFailure)
            }
            assertEquals(result, ScanCache.decode(ScanCache.encode("apk", "", result), "apk", "") {})
            assertEquals(8, ScanProtocol.RULES)
        }
    }

    @Test fun membershipContainersAndMissingResourcesUseTheSameSwitch() {
        bridge().use { bridge ->
            val ids = resources()
            fun scan(missing: Set<String> = emptySet()) = LayoutScanner(listOf(bridge), { name, kind ->
                if ("$kind/$name" in missing) 0 else ids["$kind/$name"] ?: 0
            }, { descriptor ->
                val exists = when {
                    '(' in descriptor -> bridge.getMethodData(descriptor) != null
                    "->" in descriptor -> bridge.getFieldData(descriptor) != null
                    else -> bridge.getClassData(descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')) != null
                }
                assertTrue("描述符必须属于当前 APK：$descriptor", exists)
            }).scan { _, _ -> }
            val result = scan()
            val output = System.getProperty("scan.output").orEmpty()
            if (output.isNotEmpty()) File(output).writeText(result.encode())
            assertTrue(K.MINE_VIP in result.capabilities.getValue("mine.header"))
            val membership = ids["id/member_benefit_container"] ?: 0
            if (membership != 0) {
                assertEquals(membership, result.resourceIds["id/member_benefit_container"])
                assertTrue("resource:id/member_benefit_container" in result.requirements.getValue("mine.header"))
            }
            ScanCache.verifyResources(result) { name, kind -> ids["$kind/$name"] ?: 0 }
            assertEquals("新版会员容器应独立支持原会员卡开关", membership != 0,
                K.MINE_VIP in scan(setOf("id/vip_layout")).matched)
            assertTrue("缺少新版容器时保留旧版路径", K.MINE_VIP in scan(setOf("id/member_benefit_container")).matched)
            val missingMembership = scan(setOf("id/vip_layout", "id/member_benefit_container"))
            assertFalse(K.MINE_VIP in missingMembership.matched)
            assertTrue(K.MINE_ACCOUNT in missingMembership.matched)
        }
    }

    @Test fun scansRealApkWithProductionRules() {
        bridge().use { bridge ->
            val ids = resources()
            fun scan(verify: (String) -> Unit = {}, bridges: List<DexKitBridge> = listOf(bridge),
                     missingResources: Set<String> = emptySet()): LayoutResolution =
                LayoutScanner(bridges, { name, kind -> if ("$kind/$name" in missingResources) 0 else ids["$kind/$name"] ?: 0 }, { descriptor ->
                    assertFalse("静态初始化器无法用于 Java 反射复核", "-><clinit>" in descriptor)
                    val exists = when {
                        '(' in descriptor -> bridges.any { it.getMethodData(descriptor) != null }
                        "->" in descriptor -> bridges.any { it.getFieldData(descriptor) != null }
                        else -> bridges.any { it.getClassData(descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')) != null }
                    }
                    assertTrue("描述符必须属于当前 APK：$descriptor", exists)
                    verify(descriptor)
                }).scan { checked, matched ->
                    assertTrue(checked.containsAll(matched))
                }
            val result = scan()
            val output = System.getProperty("scan.output").orEmpty()
            if (output.isNotEmpty()) File(output).writeText(result.encode())
            assertEquals("失败来源：${result.issues}", ScanProtocol.keys, result.matched)
            listOf("sport.identity", "sport.navigation", "sport.discovery", "sport.quick.discovery", "sport.fixed").forEach {
                assertTrue("缺少运动页能力：$it，${result.issues}", it in result.groups)
            }
            listOf("bottom.badges", "mine.badges.header", "mine.badges.rows", "mine.badges.grid", "mine.badges.kaka").forEach {
                assertTrue("缺少角标能力：$it", it in result.groups)
            }
            assertEquals(setOf(K.BOTTOM_BADGES), result.capabilities.getValue("bottom.badges"))
            assertFalse(K.BOTTOM_BADGES in result.capabilities.getValue("bottom.tabs"))
            assertFalse(K.MINE_BADGES in result.capabilities.getValue("mine.rows"))
            val bottomRole = "${love.nairain.huawei.hook.symbols.HuaweiHealthHookPoints.ANCHORS.bottomBase}#badges.update#2"
            val missingBadgeUpdate = scan({ if (it == result.bindings.getValue(bottomRole)) throw NoSuchMethodException() })
            assertFalse(K.BOTTOM_BADGES in missingBadgeUpdate.matched)
            assertTrue(K.BOTTOM_MINE in missingBadgeUpdate.matched)
            val ambiguousBadgeUpdate = scan({ if (it == result.bindings.getValue(bottomRole)) throw IllegalArgumentException("ambiguous") })
            assertEquals("ambiguous", ambiguousBadgeUpdate.failures[K.BOTTOM_BADGES])
            assertTrue(K.BOTTOM_MINE in ambiguousBadgeUpdate.matched)
            assertTrue(runCatching {
                LayoutResolution.decode(result.copy(bindings = result.bindings - bottomRole).encode())
            }.isFailure)
            val missingMessageBadge = scan(missingResources = setOf("id/txt_message_count_new"))
            assertFalse("mine.badges.header" in missingMessageBadge.groups)
            assertTrue("mine.badges.rows" in missingMessageBadge.groups)
            val missingRowBadge = scan(missingResources = setOf("id/list_content_red_point"))
            assertFalse("mine.badges.rows" in missingRowBadge.groups)
            assertTrue("mine.badges.header" in missingRowBadge.groups)
            listOf("mine.badges.grid" to setOf("item_channel_red_point", "item_channel_red_point_vertical"),
                "mine.badges.kaka" to setOf("kaka_to_be_collected_text")).forEach { (source, resources) ->
                assertEquals(setOf(K.MINE_BADGES), result.capabilities.getValue(source))
                resources.forEach { name ->
                    assertTrue("resource:id/$name" in result.requirements.getValue(source))
                    val missing = scan(missingResources = setOf("id/$name"))
                    assertFalse(source in missing.groups)
                    listOf("mine.badges.header", "mine.badges.rows", "mine.badges.grid", "mine.badges.kaka")
                        .filter { it != source }.forEach { assertTrue(it in missing.groups) }
                    // 单路径失败不阻止其他路径共享开关，也不使明确的失败缓存反复重扫。
                    assertTrue(K.MINE_BADGES in missing.matched)
                    assertEquals(missing, ScanCache.decode(ScanCache.encode("apk", "", missing), "apk", "") {})
                }
                val role = if (source.endsWith("grid"))
                    "com.huawei.ui.main.stories.userprofile.card.PersonalGridCardViewHolder#badges.grid.update#1"
                else "com.huawei.ui.main.stories.userprofile.scroll.CustomHeadView#badges.kaka.update#1"
                val update = result.bindings.getValue(role)
                val missing = scan({ if (it == update) throw NoSuchMethodException() })
                assertFalse(source in missing.groups)
                assertTrue(K.MINE_BADGES in missing.matched)
                val ambiguous = scan({ if (it == update) throw IllegalArgumentException("ambiguous") })
                assertEquals("ambiguous", ambiguous.issues["$source:${K.MINE_BADGES}"])
                assertFalse(source in ambiguous.groups)
                assertTrue(runCatching {
                    LayoutResolution.decode(result.copy(bindings = result.bindings - role).encode())
                }.isFailure)
            }
            assertEquals(result, ScanCache.decode(ScanCache.encode("apk", "", result), "apk", "") {})
            listOf("health.edit-cards", "sport.quick-entry-bind", "device.refresh.0", "device.refresh.1",
                "device.new.delegates", "mine.marketing").forEach { assertTrue("缺少 $it", it in result.groups) }
            assertEquals(10, result.capabilities.getValue("device.new.arkui").size)
            assertEquals(9, result.identities.keys.count { it.startsWith("delegate:") })
            assertEquals(result, LayoutResolution.decode(result.encode()))
            assertTrue(result.bindings.values.all(result.descriptors::contains))
            result.bindings.values.filter { '(' in it }.forEach {
                assertTrue("Hook 的类入口同样需要证据", it.substringBefore("->") in result.descriptors)
            }
            assertTrue(result.resourceIds.values.all { it > 0 })
            val bind = result.bindings.getValue("com.huawei.health.marketing.views.ColumnLayoutAdapter#quick.bind#2")
            assertTrue(bind.endsWith(";I)V"))
            assertTrue(result.bindings.keys.any { it.endsWith("#quick.root#field") })
            assertEquals(result, scan()) // 扫描 API 不再接收版本或已知版本标记。

            val missingRoot = scan({ symbol ->
                if (symbol == result.bindings.values.single { it.contains("->") && it.endsWith(":Landroid/widget/RelativeLayout;") }) throw NoSuchFieldException()
            })
            assertFalse("sport.quick-entry-bind" in missingRoot.groups)
            assertFalse(K.SPORT_MASTER_YOGA in missingRoot.matched)
            assertTrue(K.HEALTH_EDIT_CARDS in missingRoot.matched)
            val missingEdit = scan({ if (it == result.bindings.getValue("com.huawei.ui.homehealth.functionsetcard.FunctionSetCardViewHolder#edit.update#0")) throw NoSuchMethodException() })
            assertFalse(K.HEALTH_EDIT_CARDS in missingEdit.matched)
            assertTrue(K.HEALTH_QUICK_ENTRIES in missingEdit.matched)
            val missingCallback = scan({ if (it == result.bindings.getValue("mine.marketing")) throw NoSuchMethodException() })
            assertFalse(K.MINE_MARKETING in missingCallback.matched)
            assertTrue(K.MINE_GROUP in missingCallback.matched)
            val missingTab = scan(missingResources = setOf("id/track_sport_tab"))
            assertFalse(K.SPORT_RUN_SUMMARY in missingTab.capabilities["sport.chrome"].orEmpty())
            assertFalse(K.SPORT_PLAN_CARDS in missingTab.matched)
            assertTrue(K.SPORT_MASTER_YOGA in missingTab.matched)
            val missingPage = scan({ if (it == "L${love.nairain.huawei.hook.symbols.HuaweiHealthHookPoints.ANCHORS.deviceFragments[0].replace('.', '/')};") throw ClassNotFoundException() })
            assertFalse("device.fragment.0" in missingPage.groups)
            assertTrue("device.fragment.1" in missingPage.groups)
            assertEquals(10, missingPage.capabilities.getValue("device.new.arkui").size)
            val fixture = System.getProperty("scan.fixture").orEmpty()
            if (fixture.isNotEmpty()) DexKitBridge.create(arrayOf(File(fixture).readBytes())).use { collision ->
                val ambiguous = scan(bridges = listOf(bridge, collision))
                assertFalse(K.HEALTH_ACTIVITY_RINGS in ambiguous.matched)
                assertFalse(K.MINE_GROUP in ambiguous.matched)
                assertTrue(K.HEALTH_QUICK_ENTRIES in ambiguous.matched)
                assertTrue(K.MINE_FAMILY in ambiguous.matched)
                assertEquals("ambiguous", ambiguous.failures[K.HEALTH_ACTIVITY_RINGS])
            }
        }
    }
}
