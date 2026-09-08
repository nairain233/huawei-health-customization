package love.nairain.huawei

import love.nairain.huawei.hook.HookInstallPolicy
import love.nairain.huawei.hook.HookInstallGate
import love.nairain.huawei.hook.HookRegistryRunner
import love.nairain.huawei.hook.InstallResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookOrchestrationTest {
    @Test
    fun onlyAttachedTargetMainProcessIsAcceptedAcrossVersions() {
        val valid = HookInstallPolicy.canInstallRuntime(
            true,
            "com.huawei.health",
            "com.huawei.health",
            true,
            "17.0.7.310",
            1700007310L,
        )
        assertTrue(valid)
        assertFalse(HookInstallPolicy.canInstallRuntime(false, "com.huawei.health", "com.huawei.health", true, "17.0.7.310", 1700007310L))
        assertFalse(HookInstallPolicy.canInstallRuntime(true, "com.huawei.health", "com.huawei.health:remote", true, "17.0.7.310", 1700007310L))
        assertFalse(HookInstallPolicy.canInstallRuntime(true, "com.huawei.health", "com.huawei.health", false, "17.0.7.310", 1700007310L))
        assertTrue(HookInstallPolicy.canInstallRuntime(true, "com.huawei.health", "com.huawei.health", true, null, 1700007310L))
        assertTrue(HookInstallPolicy.canInstallRuntime(true, "com.huawei.health", "com.huawei.health", true, "17.0.7.311", 1700007310L))
        assertTrue(HookInstallPolicy.canInstallRuntime(true, "com.huawei.health", "com.huawei.health", true, "17.0.7.310", 1700007311L))
        assertTrue(HookInstallPolicy.canInstallRuntime(true, "com.huawei.health", "com.huawei.health", true, "17.0.7.311", 1700007311L))
    }

    @Test
    fun registryReportsRealResultAndIsolatesFailures() {
        val results = HookRegistryRunner.run(listOf("ok", "disabled", "boom"), { it }) {
            when (it) {
                "ok" -> InstallResult.Installed(2)
                "disabled" -> InstallResult.Disabled
                else -> error("broken")
            }
        }
        assertEquals(InstallResult.Installed(2), results["ok"])
        assertEquals(InstallResult.Disabled, results["disabled"])
        assertTrue(results["boom"] is InstallResult.Failed)
    }

    @Test
    fun repeatedAttachAndRuntimeCallbacksAreIdempotent() {
        val gate = HookInstallGate()
        assertTrue(gate.claimAttach())
        assertFalse(gate.claimAttach())
        assertTrue(gate.claimRuntime())
        assertFalse(gate.claimRuntime())
    }
}
