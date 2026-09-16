package love.nairain.huawei.scan

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import love.nairain.huawei.config.SettingsKeys

/** 与进程内共用查询规则；桌面测试只证明 Dex 定位，不能替代运行时类加载及设备回归。 */
class ApkScanTest {
    @Test fun scansRealApkWithProductionRules() {
        val native = System.getProperty("scan.native").orEmpty()
        assumeTrue("未配置桌面 DexKit 库", native.isNotEmpty())
        val library = File(native)
        listOf("libunwind.dll", "libc++.dll").map { File(library.parentFile, it) }.filter { it.isFile }.forEach { System.load(it.absolutePath) }
        System.load(library.absolutePath)
        val resourceXml = File(requireNotNull(System.getProperty("scan.resources"))).readText()
        val ids = Regex("<public type=\"([^\"]+)\" name=\"([^\"]+)\" id=\"0x([0-9a-fA-F]+)\"")
            .findAll(resourceXml).associate { "${it.groupValues[1]}/${it.groupValues[2]}" to it.groupValues[3].toLong(16).toInt() }
        DexKitBridge.create(requireNotNull(System.getProperty("scan.apk"))).use { bridge ->
            var checked = emptySet<String>()
            val result = LayoutScanner(listOf(bridge), { name, kind -> ids["$kind/$name"] ?: 0 }, {}, true).scan { p, m ->
                assertTrue(p.containsAll(checked))
                assertTrue(p.containsAll(m))
                checked = p
            }
            assertEquals(ScanProtocol.keys, checked)
            val output = System.getProperty("scan.output").orEmpty()
            if (output.isNotEmpty()) File(output).writeText(result.encode())
            assertEquals("基准 APK 的所有必要条件应命中", ScanProtocol.keys, result.matched)
            assertTrue(SettingsKeys.HEALTH_QUICK_ENTRIES in result.matched)
            assertTrue("health.edit-cards" in result.groups)
            assertFalse("health.health-cards" in result.groups)
            assertTrue(SettingsKeys.MINE_MARKETING in result.matched)
            assertTrue("mine.marketing" in result.groups)
            assertTrue("必须覆盖复用恢复入口", "sport.quick-entry-bind" in result.groups)

            val generic = LayoutScanner(listOf(bridge), { name, kind -> ids["$kind/$name"] ?: 0 }, {}, false).scan { _, _ -> }
            assertTrue(SettingsKeys.MINE_GROUP in generic.matched)
            assertFalse("未知版本不能使用编辑卡片的旧符号后备", SettingsKeys.HEALTH_EDIT_CARDS in generic.matched)
            if (output.isNotEmpty()) File("$output.generic.json").writeText(generic.encode())

            val missing = LayoutScanner(listOf(bridge), { name, kind -> ids["$kind/$name"] ?: 0 }, { symbol ->
                if (symbol.startsWith("Lcom/huawei/ui/homehealth/adapter/HomeCardAdapter;")) throw ClassNotFoundException()
            }, true).scan { _, _ -> }
            assertFalse(SettingsKeys.HEALTH_ACTIVITY_RINGS in missing.matched)
            assertTrue(SettingsKeys.MINE_GROUP in missing.matched)

            val fixture = System.getProperty("scan.fixture").orEmpty()
            if (fixture.isNotEmpty()) DexKitBridge.create(arrayOf(File(fixture).readBytes())).use { collision ->
                val ambiguous = LayoutScanner(listOf(bridge, collision), { name, kind -> ids["$kind/$name"] ?: 0 }, {}, true).scan { _, _ -> }
                assertFalse(SettingsKeys.MINE_GROUP in ambiguous.matched)
                assertEquals("ambiguous", ambiguous.failures[SettingsKeys.MINE_GROUP])
                assertTrue(SettingsKeys.MINE_FAMILY in ambiguous.matched)
            }
        }
    }
}
