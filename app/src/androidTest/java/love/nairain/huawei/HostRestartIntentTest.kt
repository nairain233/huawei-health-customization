package love.nairain.huawei

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.hook.HookInstallPolicy
import love.nairain.huawei.settings.HostRestarter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用真实 Android Intent 验证入口，不调用结束进程操作。 */
@RunWith(AndroidJUnit4::class)
class HostRestartIntentTest {
    private val pkg = HookInstallPolicy.TARGET_PACKAGE
    private fun launcher() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(pkg, "$pkg.TestLauncher"))
    private fun activity() = ActivityInfo().apply {
        packageName = pkg; name = "$pkg.TestLauncher"; enabled = true
        applicationInfo = ApplicationInfo().apply { packageName = pkg; enabled = true }
    }

    @Test fun verifiedLauncherIsCopiedWithRestartFlags() {
        val original = launcher()
        val verified = requireNotNull(HostRestarter.validatedLauncher(original, activity()))
        assertEquals(original.component, verified.component)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, verified.flags)
        assertEquals(0, original.flags)
        assertEquals(Intent.ACTION_MAIN, verified.action)
        assertTrue(verified.hasCategory(Intent.CATEGORY_LAUNCHER))
    }

    @Test fun missingImplicitForeignMismatchedAndDisabledTargetsAreRejected() {
        assertNull(HostRestarter.validatedLauncher(null, activity()))
        assertNull(HostRestarter.validatedLauncher(launcher(), null))
        assertNull(HostRestarter.validatedLauncher(Intent(Intent.ACTION_MAIN), activity()))
        assertNull(HostRestarter.validatedLauncher(launcher().setPackage("foreign.app"), activity()))
        assertNull(HostRestarter.validatedLauncher(launcher().setComponent(ComponentName("foreign.app", "foreign.app.Main")), activity()))
        assertNull(HostRestarter.validatedLauncher(launcher(), activity().apply { packageName = "foreign.app" }))
        assertNull(HostRestarter.validatedLauncher(launcher(), activity().apply { name = "$pkg.Other" }))
        assertNull(HostRestarter.validatedLauncher(launcher(), activity().apply { enabled = false }))
        assertNull(HostRestarter.validatedLauncher(launcher(), activity().apply { applicationInfo.enabled = false }))
        assertNull(HostRestarter.validatedLauncher(launcher().apply { selector = Intent(Intent.ACTION_VIEW) }, activity()))
    }
}
