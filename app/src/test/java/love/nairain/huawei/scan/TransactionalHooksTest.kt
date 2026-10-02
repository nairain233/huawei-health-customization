package love.nairain.huawei.scan

import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.hook.TransactionalHooks
import love.nairain.huawei.hook.util.ModuleLogger
import love.nairain.huawei.hook.util.LayoutCallbackScope
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

    private inner class Fixture {
        val callbacks = mutableListOf<XposedInterface.Hooker>()
        val logs = mutableListOf<String>()
        var removals = 0
        val framework = proxy<XposedInterface> { name, args ->
            when (name) {
                "hook" -> proxy<XposedInterface.HookBuilder> { method, params ->
                    if (method == "intercept") {
                        callbacks += params.single() as XposedInterface.Hooker
                        proxy<XposedInterface.HookHandle> { action, _ ->
                            if (action == "unhook") removals++
                            null
                        }
                    } else null
                }
                "log" -> { logs += args.filterIsInstance<String>().joinToString(" "); null }
                else -> null
            }
        }
        val hooks = TransactionalHooks(framework, ModuleLogger(framework))
        val origin = Target::class.java.getMethod("sample")
        val chain = proxy<XposedInterface.Chain> { name, _ -> if (name == "proceed") "original" else null }
    }

    @Test fun failedAndEmptyInstallationsNeverRegisterQueuedObservers() {
        val fixture = Fixture()
        var observers = 0
        assertTrue(runCatching { fixture.hooks.install("failed.page") {
            fixture.hooks.callbacks().afterActivation { observers++ }
            fixture.hooks.hook(fixture.origin).intercept { "changed" }
            error("later registration failed")
        } }.isFailure)
        assertEquals(0, fixture.hooks.install("empty.page") {
            fixture.hooks.callbacks().afterActivation { observers++ }
            fixture.hooks.hook(fixture.origin).intercept { "changed" }
            0
        })
        assertEquals(0, observers)
        assertEquals(2, fixture.removals)
        fixture.callbacks.forEach { assertEquals("original", it.intercept(fixture.chain)) }
    }

    @Test fun activationFailureRollsBackHooksAndDoesNotLogPrivateMessage() {
        val fixture = Fixture()
        assertEquals(0, fixture.hooks.install("failed.replay") {
            fixture.hooks.callbacks().afterActivation { error("private host parameter") }
            fixture.hooks.hook(fixture.origin).intercept { "changed" }
            1
        })
        assertEquals(1, fixture.removals)
        assertEquals("original", fixture.callbacks.single().intercept(fixture.chain))
        assertTrue(fixture.logs.single().contains("failed.replay, IllegalStateException"))
        assertFalse(fixture.logs.single().contains("private host parameter"))
    }

    @Test fun asynchronousFailureMakesOnlyThatInstalledGroupForward() {
        val fixture = Fixture()
        lateinit var failed: LayoutCallbackScope
        fixture.hooks.install("first.page") {
            failed = fixture.hooks.callbacks()
            fixture.hooks.hook(fixture.origin).intercept { "first" }
            1
        }
        fixture.hooks.install("second.page") {
            fixture.hooks.hook(fixture.origin).intercept { "second" }
            1
        }
        failed.run { throw IllegalArgumentException("private callback parameter") }
        assertEquals("original", fixture.callbacks.first().intercept(fixture.chain))
        assertEquals("second", fixture.callbacks.last().intercept(fixture.chain))
        assertEquals(1, fixture.logs.size)
    }

    @Test fun originalHostExceptionIsStillPropagatedAndDoesNotDisableModuleSource() {
        val fixture = Fixture()
        lateinit var source: LayoutCallbackScope
        fixture.hooks.install("original.failure") {
            source = fixture.hooks.callbacks()
            fixture.hooks.hook(fixture.origin).intercept { it.proceed() }
            1
        }
        val failingHost = proxy<XposedInterface.Chain> { name, _ ->
            if (name == "proceed") throw IllegalStateException("original host exception") else null
        }
        assertThrows(IllegalStateException::class.java) { fixture.callbacks.single().intercept(failingHost) }
        assertTrue(source.isActive)
        assertTrue(fixture.logs.isEmpty())
    }
}
