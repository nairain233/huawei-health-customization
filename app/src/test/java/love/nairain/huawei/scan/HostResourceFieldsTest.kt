package love.nairain.huawei.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class HostResourceFieldsTest {
    private fun resolve(
        vararg owners: Class<*>,
        kind: String = "string",
        packageName: String = "com.huawei.health",
    ): Int = HostResourceFields.resolve(owners.toList(), "title", kind, packageName,
        { if (it == 100 || it == 101) "string" else throw IllegalArgumentException("missing") },
        { "com.huawei.health" })

    @Test fun acceptsBothFinalAndNonFinalPublicStaticInts() {
        assertEquals(100, resolve(MutableR::class.java))
        assertEquals(100, resolve(ConstantR::class.java, MutableR::class.java))
    }

    @Test fun rejectsWrongFieldTypeInstanceAndPrivateFields() {
        assertEquals(0, resolve(LongR::class.java, InstanceR::class.java, PrivateR::class.java))
    }

    @Test fun rejectsWrongResourceTypePackageAndMissingResources() {
        assertEquals(0, resolve(MutableR::class.java, kind = "id"))
        assertEquals(0, resolve(MutableR::class.java, packageName = "other.app"))
        assertEquals(0, resolve(MissingR::class.java, ZeroR::class.java))
    }

    @Test fun rejectsConflictingIdsButIgnoresMissingFields() {
        assertEquals(0, resolve(MutableR::class.java, ConflictingR::class.java))
        assertEquals(100, resolve(String::class.java, MutableR::class.java))
    }

    class MutableR { companion object { @JvmField var title = 100 } }
    class ConstantR { companion object { const val title = 100 } }
    class ConflictingR { companion object { @JvmField var title = 101 } }
    class LongR { companion object { @JvmField var title = 100L } }
    class InstanceR { @JvmField var title = 100 }
    class PrivateR { companion object { private const val title = 100 } }
    class MissingR { companion object { @JvmField var title = 999 } }
    class ZeroR { companion object { @JvmField var title = 0 } }
}
