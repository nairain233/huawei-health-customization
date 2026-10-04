package love.nairain.huawei.scan

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge

class StructuralScanTest {
    @Test fun renamedBindingsSurviveWhileAmbiguousOrMissingRootsStopOnlyThatPath() {
        val native = System.getProperty("scan.native").orEmpty()
        val fixtures = System.getProperty("scan.structure").orEmpty()
        assumeTrue("未配置结构 DEX 夹具", native.isNotEmpty() && fixtures.isNotEmpty())
        val library = File(native)
        listOf("libunwind.dll", "libc++.dll").map { File(library.parentFile, it) }.forEach { System.load(it.absolutePath) }
        System.load(library.absolutePath)
        fun scan(variant: String): LayoutResolution {
            val bytes = File(fixtures, "$variant/dex/classes.dex").readBytes()
            return DexKitBridge.create(arrayOf(bytes)).use { bridge ->
                LayoutScanner(listOf(bridge), { name, kind ->
                    if (kind == "id" && name in setOf("sport_viewPager_container", "item_quick_entry_root_layout"))
                        if (name == "sport_viewPager_container") 1 else 2
                    else 0
                }, {}).scan { _, _ -> }
            }
        }
        val first = scan("first")
        val renamed = scan("renamed")
        val role = "com.huawei.health.marketing.views.ColumnLayoutAdapter#quick.bind#2"
        assertTrue("sport.quick-entry-bind" in first.groups)
        assertTrue("sport.quick-entry-bind" in renamed.groups)
        assertTrue(first.bindings.getValue(role).contains("->x("))
        assertTrue(renamed.bindings.getValue(role).contains("->p("))
        assertTrue(first.bindings.values.any { it.contains("->cn:") })
        assertTrue(renamed.bindings.values.any { it.contains("->zz:") })
        assertFalse(renamed.bindings.values.any { it.contains("->other:") })
        val ambiguous = scan("ambiguous")
        assertFalse("sport.quick-entry-bind" in ambiguous.groups)
        assertEquals("ambiguous", ambiguous.issues["sport.quick-entry-bind"])
        assertFalse(role in ambiguous.bindings)
        val missing = scan("missing-root")
        assertFalse("sport.quick-entry-bind" in missing.groups)
        assertFalse(role in missing.bindings)
    }
}
