package love.nairain.huawei.app

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import love.nairain.huawei.config.SettingsKeys
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 模块应用：负责与 LSPosed 服务建立连接并通知设置界面。
 */
class ModuleApplication : Application(), XposedServiceHelper.OnServiceListener {
    private val listeners = CopyOnWriteArraySet<ServiceStateListener>()
    internal lateinit var layoutSettingsCoordinator: LayoutSettingsCoordinator
        private set
    internal lateinit var scopeSettingsCoordinator: ScopeSettingsCoordinator
        private set
    internal var colorMode by mutableStateOf(AppColorMode.SYSTEM)
        private set
    private lateinit var settingsExecutor: ExecutorService

    override fun onCreate() {
        super.onCreate()
        colorMode = AppAppearancePreferences.read(this)
        settingsExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "layout-config").apply { isDaemon = true }
        }
        layoutSettingsCoordinator = LayoutSettingsCoordinator(
            workerExecutor = settingsExecutor,
            mainExecutor = mainExecutor,
        )
        scopeSettingsCoordinator = ScopeSettingsCoordinator(
            workerExecutor = settingsExecutor,
            mainExecutor = mainExecutor,
        )
        XposedServiceHelper.registerListener(this)
    }

    internal fun updateColorMode(newColorMode: AppColorMode) {
        if (colorMode == newColorMode) return
        AppAppearancePreferences.write(this, newColorMode)
        colorMode = newColorMode
    }

    override fun onServiceBind(boundService: XposedService) {
        service = boundService
        layoutSettingsCoordinator.bind {
            boundService.getRemotePreferences(SettingsKeys.GROUP)
        }
        scopeSettingsCoordinator.bind(XposedScopeService(boundService))
        notifyListeners()
    }

    override fun onServiceDied(deadService: XposedService) {
        if (service === deadService) {
            service = null
            layoutSettingsCoordinator.bind(null)
            scopeSettingsCoordinator.bind(null)
            notifyListeners()
        }
    }

    fun addServiceStateListener(listener: ServiceStateListener) {
        listeners.add(listener)
        listener.onServiceStateChanged(service)
    }

    fun removeServiceStateListener(listener: ServiceStateListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            try {
                listener.onServiceStateChanged(service)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Service state listener failed", error)
            }
        }
    }

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    companion object {
        private const val TAG = "HuaweiTrim"
        @Volatile
        var service: XposedService? = null
            private set
    }
}
