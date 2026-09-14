package love.nairain.huawei.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import love.nairain.huawei.R
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServiceConfigStore
import love.nairain.huawei.config.ServiceSaveResult
import love.nairain.huawei.config.SettingsKeys
import java.util.concurrent.Executors

internal data class ServiceBlockUiState(
    val catalog: ServiceCatalog = ServiceCatalog(),
    val config: ServiceBlockConfig = ServiceBlockConfig(),
    val connected: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val message: Int? = null,
) {
    val writable: Boolean get() = connected && !loading && !saving
}

/** 只负责目录展示与配置，包查询、同步提交均放在单一工作线程。 */
class ServiceBlockActivity : AppCompatActivity(), ModuleApplication.ServiceStateListener {
    private var state by mutableStateOf(ServiceBlockUiState())
    private val worker = Executors.newSingleThreadExecutor()
    private var service: XposedService? = null
    private var revision = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ModuleApplication
        setContent {
            HuaweiTrimTheme(colorMode = app.colorMode, window = window) {
                ServiceBlockScreen(state, ::save, ::refresh, ::finish)
            }
        }
        app.addServiceStateListener(this)
    }

    override fun onResume() {
        super.onResume()
        if (!state.saving) refresh()
    }

    override fun onDestroy() {
        (application as ModuleApplication).removeServiceStateListener(this)
        revision++
        worker.shutdown()
        super.onDestroy()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        runOnUiThread {
            this.service = service
            refresh()
        }
    }

    private fun refresh() {
        if (isDestroyed || worker.isShutdown) return
        val request = ++revision
        val boundService = service
        state = state.copy(loading = true, connected = false)
        worker.execute {
            val catalog = try {
                ServiceCatalog.load(packageManager)
            } catch (_: PackageManager.NameNotFoundException) {
                ServiceCatalog(status = R.string.service_block_not_installed)
            } catch (_: RuntimeException) {
                ServiceCatalog(status = R.string.service_block_load_error)
            }
            var message: Int? = null
            val config = if (boundService == null) {
                message = R.string.service_block_disconnected
                null
            } else try {
                ServiceBlockConfig.read(boundService.getRemotePreferences(SettingsKeys.GROUP))
            } catch (_: RuntimeException) {
                message = R.string.settings_status_config_error
                null
            }
            runOnUiThread {
                if (!isDestroyed && request == revision) {
                    state = ServiceBlockUiState(catalog, config ?: state.config,
                        connected = config != null, loading = false, message = message)
                }
            }
        }
    }

    private fun save(next: ServiceBlockConfig) {
        val boundService = service ?: return
        if (!state.writable) return
        val previous = state.config
        // 未支持或未安装时仍允许关闭开关、删除旧规则，不允许新增拦截。
        if (!state.catalog.supported && ((!previous.enabled && next.enabled) ||
                (next.components - previous.components).isNotEmpty())) return
        val request = ++revision
        state = state.copy(config = next, saving = true, message = null)
        worker.execute {
            val result = try {
                ServiceConfigStore.save(boundService.getRemotePreferences(SettingsKeys.GROUP), previous, next)
            } catch (_: RuntimeException) {
                ServiceSaveResult(false, false)
            }
            runOnUiThread {
                if (!isDestroyed && request == revision) {
                    state = state.copy(
                        config = if (result.saved) next else previous,
                        saving = false,
                        connected = state.connected && result.restored,
                        message = when {
                            result.saved -> R.string.settings_restart_notice
                            !result.restored -> R.string.service_block_save_uncertain
                            else -> R.string.settings_status_save_error
                        },
                    )
                }
            }
        }
    }
}
