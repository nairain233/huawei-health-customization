package love.nairain.huawei.watchface

import java.io.File
import java.lang.reflect.Modifier
import love.nairain.huawei.hook.resolver.LocalWatchFaceTargets
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge

/** 直接验证生产解析器的每个方法契约，不能替代 GT4 真机测试。 */
class LocalWatchFaceApkTest {
    @Test fun exactContractsExistInRealApk() {
        val native = System.getProperty("scan.native").orEmpty()
        assumeTrue("未配置桌面 DexKit", native.isNotBlank())
        val library = File(native)
        listOf("libunwind.dll", "libc++.dll").map { File(library.parentFile, it) }.filter { it.isFile }
            .forEach { System.load(it.absolutePath) }
        System.load(library.absolutePath)
        val version = System.getProperty("scan.version").orEmpty().ifEmpty { "17.0.7.320" }
        val code = when (version) {
            "17.0.7.320" -> 1700007320L
            "17.0.8.300" -> 1700008300L
            else -> error("未配置已核验的表盘版本")
        }
        DexKitBridge.create(requireNotNull(System.getProperty("scan.apk"))).use { bridge ->
            LocalWatchFaceTargets.specs(version, code).forEach { spec ->
                val found = bridge.findMethod {
                    matcher { declaredClass(spec.owner); name(spec.name); returnType(spec.result); paramTypes(*spec.args.toTypedArray()) }
                }
                assertEquals("${spec.key}: ${spec.owner}.${spec.name}", 1, found.size)
                assertEquals(spec.key, spec.static, Modifier.isStatic(found.single().modifiers))
            }
            assertTrue(bridge.getClassData(LocalWatchFaceTargets.INFO)!!.methods.any { it.name == "<init>" && it.paramTypeNames.isEmpty() })
            val callbacks = bridge.getClassData(LocalWatchFaceTargets.BT)!!.fields.single { it.name == "mOperateCallbacks" }
            assertEquals("java.util.LinkedHashMap", callbacks.typeName)
            assertFalse(Modifier.isStatic(callbacks.modifiers))
            LocalWatchFaceTargets.callbackFields.forEach { spec ->
                val field = bridge.getClassData(LocalWatchFaceTargets.MANAGER)!!.fields.single { it.name == spec.name }
                assertEquals(spec.key, spec.type, field.typeName)
                assertFalse(Modifier.isStatic(field.modifiers))
            }
        }
        val resources = File(requireNotNull(System.getProperty("scan.resources"))).readText()
        listOf("web_view").forEach { assertTrue(resources.contains("name=\"$it\"")) }
    }
}
