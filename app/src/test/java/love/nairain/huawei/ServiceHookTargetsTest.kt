package love.nairain.huawei

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.UserHandle
import love.nairain.huawei.hook.HookInstallPolicy
import love.nairain.huawei.hook.resolver.ServiceHookTargets
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class ServiceHookTargetsTest {
    @Test fun resolvesVerifiedSignatureWithoutAndroidRuntime() {
        val targets = ServiceHookTargets.resolve(VerifiedContext::class.java)
        assertEquals("startServiceCommon", targets.start.name)
        assertEquals("long", targets.bind.parameterTypes[2].name)
        assertEquals("unbindService", targets.unbind.name)
        assertEquals("getOuterContext", targets.outerContext.name)
    }

    @Test fun unknownSignatureAndMissingMethodsFailBeforeInstallation() {
        assertThrows(IllegalArgumentException::class.java) { ServiceHookTargets.resolve(UnknownContext::class.java) }
        assertThrows(IllegalArgumentException::class.java) { ServiceHookTargets.resolve(Any::class.java) }
    }

    @Test fun packageAndProcessRemainStrictDespiteServiceFeature() {
        assertFalse(HookInstallPolicy.acceptsPackage("other.app", "other.app", true))
        assertFalse(HookInstallPolicy.acceptsPackage("com.huawei.health", "com.huawei.health:service", true))
        assertFalse(HookInstallPolicy.acceptsVersion("unknown", 0))
        assertTrue(HookInstallPolicy.acceptsPackage("com.huawei.health", "com.huawei.health", true))
    }

    @Suppress("UNUSED_PARAMETER")
    private class VerifiedContext {
        fun startServiceCommon(intent: Intent, foreground: Boolean, user: UserHandle): ComponentName? = null
        fun bindServiceCommon(intent: Intent, connection: ServiceConnection, flags: Long,
            instance: String?, handler: Handler?, executor: Executor?, user: UserHandle): Boolean = false
        fun unbindService(connection: ServiceConnection) = Unit
        fun getOuterContext(): Context? = null
    }

    @Suppress("UNUSED_PARAMETER")
    private class UnknownContext {
        fun startServiceCommon(intent: Intent, foreground: Boolean, user: UserHandle): String? = null
    }
}
