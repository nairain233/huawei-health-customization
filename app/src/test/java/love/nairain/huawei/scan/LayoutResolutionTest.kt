package love.nairain.huawei.scan

import love.nairain.huawei.config.SettingsKeys
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class LayoutResolutionTest {
    private val descriptor = "Lsample/Holder;->q(I)V"
    private val role = "sample.Holder#quick.bind#1"
    private fun resolution() = LayoutResolution(setOf("sport.quick-entry-bind"), setOf(SettingsKeys.SPORT_STRETCH),
        mapOf(role to descriptor), setOf(descriptor), "", capabilities = mapOf("sport.quick-entry-bind" to setOf(SettingsKeys.SPORT_STRETCH)),
        resourceIds = mapOf("string/title" to 42), identities = mapOf("content:42" to SettingsKeys.SPORT_STRETCH),
        requirements = mapOf("sport.quick-entry-bind" to setOf(descriptor, "binding:$role", "resource:string/title", "identity:content:42")))

    @Test fun snapshotRoundTripPreservesExactTargetsResourcesAndDependencies() {
        assertEquals(resolution(), LayoutResolution.decode(resolution().encode()))
    }

    @Test fun missingBindingCannotBeHiddenByRemainingDescriptor() {
        assertTrue(runCatching { LayoutResolution.decode(resolution().copy(bindings = emptyMap()).encode()) }.isFailure)
    }

    @Test fun missingResourceOrIdentityInvalidatesDependentCapability() {
        assertTrue(runCatching { LayoutResolution.decode(resolution().copy(resourceIds = emptyMap()).encode()) }.isFailure)
        assertTrue(runCatching { LayoutResolution.decode(resolution().copy(identities = emptyMap()).encode()) }.isFailure)
    }

    @Test fun oldRulesAndUnprovenMatchedKeysAreRejected() {
        assertTrue(runCatching { LayoutResolution.decode(JSONObject(resolution().encode()).put("rules", 7).toString()) }.isFailure)
        assertTrue(runCatching { LayoutResolution.decode(resolution().copy(matched = ScanProtocol.keys).encode()) }.isFailure)
        assertTrue(runCatching { LayoutResolution.decode(resolution().copy(descriptors = emptySet()).encode()) }.isFailure)
    }
}
