package love.nairain.huawei.scan

import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.hook.TransactionalHooks
import love.nairain.huawei.hook.util.ModuleLogger
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class TransactionalHooksTest {
    class Target { fun sample() = "original" }
    private inline fun <reified T> proxy(noinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args.orEmpty()) } as T

    @Test fun failedGroupRollsBackAndRemainsInactiveEvenIfUnhookFails() {
        val callbacks = mutableListOf<XposedInterface.Hooker>()
        var removals = 0
        val framework = proxy<XposedInterface> { name, _ ->
            if (name == "hook") proxy<XposedInterface.HookBuilder> { method, args ->
                if (method == "intercept") {
                    callbacks += args.single() as XposedInterface.Hooker
                    proxy<XposedInterface.HookHandle> { action, _ ->
                        if (action == "unhook") { removals++; error("rollback failure") }
                        null
                    }
                } else null
            } else null
        }
        val hooks = TransactionalHooks(framework, ModuleLogger(framework))
        val origin = Target::class.java.getMethod("sample")
        val chain = proxy<XposedInterface.Chain> { name, _ -> if (name == "proceed") Target().sample() else null }
        assertTrue(runCatching { hooks.install {
            hooks.hook(origin).intercept { "changed" }
            assertEquals("original", callbacks.single().intercept(chain))
            error("second entry failed")
        } }.isFailure)
        assertEquals(1, removals)
        assertEquals("original", callbacks.single().intercept(chain))
        hooks.install { hooks.hook(origin).intercept { "next group" }; 1 }
        assertEquals("next group", callbacks.last().intercept(chain))
    }
}
