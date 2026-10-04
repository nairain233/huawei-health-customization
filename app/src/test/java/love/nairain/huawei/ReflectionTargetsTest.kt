package love.nairain.huawei

import love.nairain.huawei.hook.resolver.ReflectionTargets
import love.nairain.huawei.scan.LayoutResolution
import org.luckypray.dexkit.wrap.DexMethod
import org.luckypray.dexkit.wrap.DexField
import org.junit.Assert.*
import org.junit.Test

class ReflectionTargetsTest {
    class Renamed {
        @JvmField var moved = "root"
        @JvmField var distraction = "other"
        fun q(value: Int): String = "int:$value"
        fun q(value: String): String = "text:$value"
        fun r(): String = "identity"
    }

    private val type = Renamed::class.java
    private fun targets(): ReflectionTargets {
        val bindings = mapOf(
            "${type.name}#bind#1" to DexMethod(type.getDeclaredMethod("q", Int::class.javaPrimitiveType)).toString(),
            "${type.name}#model.identity#0" to DexMethod(type.getDeclaredMethod("r")).toString(),
            "${type.name}#root#field" to DexField(type.getDeclaredField("moved")).toString(),
        )
        return ReflectionTargets(LayoutResolution(emptySet(), emptySet(), bindings, bindings.values.toSet(), ""),
            requireNotNull(type.classLoader))
    }

    @Test fun fullDescriptorSelectsRenamedMemberAmongSameArityOverloads() {
        assertEquals("int:9", targets().method(type, "bind", 1)!!.invoke(Renamed(), 9))
        assertNull(targets().method(type, "q", 1))
        assertNull(targets().method(type, "bind", 2))
        assertNull(targets().method(type, "bind", 1, Int::class.javaPrimitiveType))
    }

    @Test fun modelGetterAndFieldRequireTheirOwnEvidence() {
        assertEquals("identity", targets().invokeNoArgs(Renamed(), "model.identity"))
        assertNull(targets().invokeNoArgs(Renamed(), "r"))
        assertEquals("root", targets().fieldValue(Renamed(), "root"))
        assertNull(targets().fieldValue(Renamed(), "distraction"))
    }
}
