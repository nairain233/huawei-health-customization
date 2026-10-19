package love.nairain.huawei

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.settings.AtomicHostConfigStorage
import love.nairain.huawei.settings.HostConfiguration
import love.nairain.huawei.settings.HostConfigStore
import love.nairain.huawei.settings.HostSaveResult
import love.nairain.huawei.settings.HostSettingsController
import love.nairain.huawei.settings.HostSettingsNotice
import love.nairain.huawei.settings.HostStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** 真正执行 AtomicFile 与 Android 主线程回调，不启动模块服务或读取旧偏好。 */
@RunWith(AndroidJUnit4::class)
class HostLocalConfigurationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun fixture(): Context {
        val base = instrumentation.targetContext
        val directory = File(base.cacheDir, "host-local-test-" + UUID.randomUUID())
        return object : ContextWrapper(base) {
            // 模拟 attach 结束但 LoadedApk 尚未发布 Application 的时刻。
            override fun getApplicationContext(): Context? = null
            override fun getNoBackupFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                error("旧偏好不应被读取")
            override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean =
                error("不应绑定模块服务")
            override fun startService(intent: Intent): ComponentName? = error("不应启动模块服务")
        }
    }

    @Test fun atomicFileUsesNewDirectoryAndPreservesCorruptContent() {
        val context = fixture()
        val storage = AtomicHostConfigStorage(context)
        val store = HostConfigStore(storage)
        val initial = store.load()
        val next = initial.copy(layout = initial.layout + (SettingsKeys.HEALTH_SEARCH to true))
        assertEquals(HostSaveResult.SAVED, store.save(next))
        assertEquals(next, HostConfigStore(AtomicHostConfigStorage(context)).load())
        assertEquals(File(context.noBackupFilesDir, "huawei_trim/scan"), HostStorage.scanDirectory(context))
        storage.write("{broken")
        assertThrows(Exception::class.java) { HostConfigStore(storage).load() }
        assertEquals("{broken", storage.read())
    }

    @Test fun saveCompletesAfterOldPageClosesAndOldCloseCannotStopNewPage() {
        val context = fixture()
        val client = HostSettingsController(context) { fail(it) }
        val initialized = CountDownLatch(1)
        val startup = AtomicReference<HostConfiguration>()
        instrumentation.runOnMainSync {
            client.initialize { startup.set(it); initialized.countDown() }
            assertFalse(client.canRestart)
        }
        assertTrue(initialized.await(5, TimeUnit.SECONDS))
        val saved = CountDownLatch(1)
        var oldCallbacks = 0
        var oldCount = 0
        var subscription: AutoCloseable? = null
        instrumentation.runOnMainSync {
            val old = client.start { oldCallbacks++ }
            assertTrue(client.canRestart)
            client.saveLayout(SettingsKeys.HEALTH_SEARCH, true)
            assertFalse(client.canRestart)
            old.close()
            oldCount = oldCallbacks
            subscription = client.start {
                if (it.notice == HostSettingsNotice.SAVED) saved.countDown()
            }
            old.close()
        }
        assertTrue(saved.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            assertEquals(oldCount, oldCallbacks)
            assertTrue(client.writable)
            assertTrue(client.canRestart)
            subscription?.close()
            assertFalse(client.writable)
            assertFalse(client.canRestart)
        }
        val persisted = HostConfigStore(AtomicHostConfigStorage(context)).load()
        assertTrue(persisted.layout.getValue(SettingsKeys.HEALTH_SEARCH))
        assertFalse(startup.get().layout.getValue(SettingsKeys.HEALTH_SEARCH))
    }

    @Test fun moduleApkHasNoLauncherSettingsServiceOrConfigurationProvider() {
        val context = instrumentation.targetContext
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_ACTIVITIES or
                android.content.pm.PackageManager.GET_SERVICES or android.content.pm.PackageManager.GET_PROVIDERS)
        assertFalse(info.activities.orEmpty().any { it.name.endsWith("ManagementActivity") || it.name.endsWith("LauncherActivity") })
        assertFalse(info.services.orEmpty().any { it.name.startsWith(context.packageName) })
        assertFalse(info.providers.orEmpty().any { it.name.startsWith(context.packageName) })
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(launcher, 0).isEmpty())
    }
}
