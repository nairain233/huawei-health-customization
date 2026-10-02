package love.nairain.huawei

import love.nairain.huawei.hook.resolver.ActiveFragmentTargets
import love.nairain.huawei.hook.util.LayoutCallbackScope
import org.junit.Assert.*
import org.junit.Test

class ActiveFragmentTargetsTest {
    class Manager(private val values: List<Any>) {
        fun getFragments() = values
    }
    class Activity(private val manager: Manager) {
        fun getSupportFragmentManager() = manager
    }
    open class Fragment(private val added: Boolean = true) {
        var children = Manager(emptyList())
        fun isAdded() = added
        fun getChildFragmentManager() = children
    }
    class Target(added: Boolean = true) : Fragment(added)

    @Test fun onlyActualTargetInstancesInAddedFragmentTreeAreReplayed() {
        val nested = Target()
        val parent = Fragment().apply { children = Manager(listOf(nested)) }
        val direct = Target()
        val inactive = Target(false)
        val found = mutableListOf<Any>()
        ActiveFragmentTargets.forEach(Activity(Manager(listOf(parent, direct, inactive))), Target::class.java, found::add)
        assertEquals(listOf(direct, nested), found)
    }

    @Test fun ordinaryActivityWithoutSupportManagerIsNotAParsingFailure() {
        ActiveFragmentTargets.forEach(Any(), Target::class.java) { fail("not a fragment host") }
    }

    @Test fun repeatedManagerIsVisitedOnceWithoutKeepingFragmentReferences() {
        val target = Target()
        val manager = Manager(listOf(target))
        target.children = manager
        var calls = 0
        ActiveFragmentTargets.forEach(Activity(manager), Target::class.java) { calls++ }
        assertEquals(1, calls)
    }

    class BrokenManager {
        fun getFragments(): List<Any> = error("private fragment failure")
    }
    class BrokenActivity {
        fun getSupportFragmentManager() = BrokenManager()
    }

    @Test fun fragmentAccessFailureDisablesOnlyItsCallbackSource() {
        val errors = mutableListOf<String>()
        val source = LayoutCallbackScope(errors::add).apply { activate() }
        source.run { ActiveFragmentTargets.forEach(BrokenActivity(), Target::class.java) { fail("unexpected fragment") } }
        assertFalse(source.isActive)
        assertEquals(listOf("InvocationTargetException"), errors)
    }
}
