package love.nairain.huawei.app

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import love.nairain.huawei.R
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServiceConfigStore
import love.nairain.huawei.config.ServicePreset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

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

internal fun interface ServicePreferencesSource {
    fun getPreferences(): SharedPreferences
}

internal fun interface ServiceSettingsListener {
    fun onSettingsChanged(state: ServiceBlockUiState)
}

/** 应用级服务配置状态。远程访问串行执行，页面仅接收已确认值。 */
internal class ServiceSettingsCoordinator(
    private val workerExecutor: Executor,
    private val mainExecutor: Executor,
    private val loadCatalog: () -> ServiceCatalog,
) {
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<ServiceSettingsListener>()
    private var binding: Binding? = null
    private var uncertainIdentity: Any? = null
    private var revision = 0L
    @Volatile private var state = ServiceBlockUiState()

    fun bind(identity: Any?, source: ServicePreferencesSource? = null) {
        require((identity == null) == (source == null))
        val task = synchronized(lock) {
            if (binding?.identity === identity) return
            binding = if (identity != null) Binding(identity, requireNotNull(source)) else null
            requestReadLocked()
        }
        executeRead(task)
    }

    fun refresh() {
        val task = synchronized(lock) {
            // 保存中的刷新和页面重建不取消正在进行的确认或回滚。
            if (state.saving) return
            requestReadLocked()
        }
        executeRead(task)
    }

    fun save(next: ServiceBlockConfig): Boolean {
        val normalized = next.copy(
            components = next.components.mapNotNull(ServiceBlockConfig::normalize).toSet(),
            presets = next.presets.filter(ServicePreset::isKnown).toSet(),
        )
        val task = synchronized(lock) {
            val current = binding ?: return false
            val previous = state.config
            if (!state.writable || normalized == previous) return false
            // 未安装时允许撤销旧规则，但不允许新增拦截。
            if (!state.catalog.supported && (
                    (!previous.enabled && normalized.enabled) ||
                    (normalized.components - previous.components).isNotEmpty() ||
                    (ServicePreset.selectedComponents(normalized.presets) -
                        ServicePreset.selectedComponents(previous.presets)).isNotEmpty() ||
                    (!previous.debugMode && normalized.debugMode && normalized.components.isNotEmpty())
                )) return false
            revision += 1
            val task = SaveTask(current, normalized)
            updateStateLocked(state.copy(saving = true, message = R.string.settings_status_saving))
            task
        }
        executeSave(task)
        return true
    }

    fun addListener(listener: ServiceSettingsListener) {
        synchronized(lock) {
            if (listeners.none { it === listener }) listeners.add(listener)
        }
        mainExecutor.execute {
            if (listeners.any { it === listener }) notifyListener(listener, state)
        }
    }

    fun removeListener(listener: ServiceSettingsListener) {
        listeners.removeIf { it === listener }
    }

    internal fun currentState(): ServiceBlockUiState = state

    private fun requestReadLocked(): ReadTask {
        revision += 1
        val current = binding
        val uncertain = current != null && current.identity === uncertainIdentity
        updateStateLocked(state.copy(
            loading = true, saving = false, connected = false,
            message = if (uncertain) R.string.service_block_save_uncertain else null,
        ))
        return ReadTask(revision, current)
    }

    private fun executeRead(task: ReadTask) {
        workerExecutor.execute {
            if (!isCurrentRead(task)) return@execute
            val catalog = try {
                loadCatalog()
            } catch (_: PackageManager.NameNotFoundException) {
                ServiceCatalog(status = R.string.service_block_not_installed)
            } catch (_: RuntimeException) {
                ServiceCatalog(status = R.string.service_block_load_error)
            }
            val uncertain = synchronized(lock) {
                task.binding != null && task.binding.identity === uncertainIdentity
            }
            val config = if (task.binding == null || uncertain) null else try {
                ServiceBlockConfig.read(task.binding.source.getPreferences())
            } catch (_: RuntimeException) {
                null
            }
            synchronized(lock) {
                if (!isCurrentReadLocked(task)) return@synchronized
                if (config != null) uncertainIdentity = null
                updateStateLocked(state.copy(
                    catalog = catalog, config = config ?: state.config,
                    connected = config != null, loading = false, saving = false,
                    message = when {
                        uncertain -> R.string.service_block_save_uncertain
                        task.binding == null -> R.string.service_block_disconnected
                        config == null -> R.string.settings_status_config_error
                        else -> null
                    },
                ))
            }
        }
    }

    private fun executeSave(task: SaveTask) {
        workerExecutor.execute {
            if (synchronized(lock) { binding !== task.binding }) return@execute
            val result = try {
                ServiceConfigStore.save(task.binding.source.getPreferences(), task.next)
            } catch (_: RuntimeException) {
                null // 获取配置或原始快照失败，尚未开始写入。
            }
            synchronized(lock) {
                if (result?.restored == false) uncertainIdentity = task.binding.identity
                if (binding?.identity !== task.binding.identity) return@synchronized
                // 同一连接重复绑定也不能掩盖旧提交的回滚失败。
                if (binding !== task.binding && result?.restored != false) return@synchronized
                updateStateLocked(state.copy(
                    config = if (result?.saved == true) task.next else state.config,
                    saving = false, loading = false,
                    connected = result != null && result.restored,
                    message = when {
                        result == null -> R.string.settings_status_config_error
                        result.saved -> R.string.settings_restart_notice
                        !result.restored -> R.string.service_block_save_uncertain
                        else -> R.string.settings_status_save_error
                    },
                ))
            }
        }
    }

    private fun isCurrentRead(task: ReadTask): Boolean = synchronized(lock) { isCurrentReadLocked(task) }

    private fun isCurrentReadLocked(task: ReadTask): Boolean =
        revision == task.revision && binding === task.binding

    private fun updateStateLocked(next: ServiceBlockUiState) {
        state = next
        mainExecutor.execute {
            if (state === next) listeners.forEach { notifyListener(it, next) }
        }
    }

    private fun notifyListener(listener: ServiceSettingsListener, snapshot: ServiceBlockUiState) {
        try {
            listener.onSettingsChanged(snapshot)
        } catch (_: RuntimeException) {
            Log.w("HuaweiTrim", "Service settings listener failed")
        }
    }

    private class Binding(val identity: Any, val source: ServicePreferencesSource)
    private data class ReadTask(val revision: Long, val binding: Binding?)
    private data class SaveTask(val binding: Binding, val next: ServiceBlockConfig)
}
