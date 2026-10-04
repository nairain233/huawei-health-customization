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

    @Test fun scansRealApkWithProductionRules() {
        val native = System.getProperty("scan.native").orEmpty()
        assumeTrue("未配置桌面 DexKit 库", native.isNotEmpty())
        val library = File(native)
        listOf("libunwind.dll", "libc++.dll").map { File(library.parentFile, it) }.filter { it.isFile }.forEach { System.load(it.absolutePath) }
        System.load(library.absolutePath)
        val ids = resources()
        DexKitBridge.create(requireNotNull(System.getProperty("scan.apk"))).use { bridge ->
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
