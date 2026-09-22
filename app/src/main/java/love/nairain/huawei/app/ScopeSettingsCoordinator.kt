package love.nairain.huawei.app

import android.util.Log
import io.github.libxposed.service.XposedService
import love.nairain.huawei.hook.HookInstallPolicy
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor

/** 作用域状态只反映 LSPosed 实际返回值，不写入 RemotePreferences。 */
internal data class ScopeUiState(
    val isServiceConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isChanging: Boolean = false,
    val isGranted: Boolean = false,
    val hasError: Boolean = false,
) {
    val writable: Boolean
        get() = isServiceConnected && !isLoading && !isChanging
}

internal fun interface ScopeSettingsListener {
    fun onScopeStateChanged(state: ScopeUiState)
}

internal interface ScopeRequestCallback {
    fun onApproved(packages: List<String>)

    fun onFailed()
}

internal interface ScopeService {
    fun getScope(): List<String>

    fun requestScope(packages: List<String>, callback: ScopeRequestCallback)

    fun removeScope(packages: List<String>)
}

/** API 102 服务适配层，避免界面与 libxposed 回调线程直接耦合。 */
internal class XposedScopeService(
    private val service: XposedService,
) : ScopeService {
    override fun getScope(): List<String> = service.getScope()

    override fun requestScope(packages: List<String>, callback: ScopeRequestCallback) {
        service.requestScope(
            packages,
            object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    callback.onApproved(approved)
                }

                override fun onScopeRequestFailed(message: String) {
                    // 框架消息可能包含实现细节，不向 UI 或模块日志透传。
                    callback.onFailed()
                }
            },
        )
    }

    override fun removeScope(packages: List<String>) {
        service.removeScope(packages)
    }
}

/** 串行读取和修改 LSPosed 作用域，并丢弃旧服务绑定的异步回调。 */
internal class ScopeSettingsCoordinator(
    private val workerExecutor: Executor,
    private val mainExecutor: Executor,
) {
    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<ScopeSettingsListener>()
    private var source: ScopeService? = null
    private var bindingRevision = 0L
    private var operationRevision = 0L

    @Volatile
    private var state = ScopeUiState()

    fun bind(newSource: ScopeService?) {
        val task = synchronized(lock) {
            bindingRevision += 1
            operationRevision += 1
            source = newSource
            if (newSource == null) {
                updateStateLocked(ScopeUiState())
                null
            } else {
                updateStateLocked(
                    state.copy(
                        isServiceConnected = true,
                        isLoading = true,
                        isChanging = false,
                        isGranted = false,
                        hasError = false,
                    ),
                )
                ReadTask(operationRevision, bindingRevision, newSource)
            }
        }
        task?.let(::executeRead)
    }

    fun refresh() {
        val task = synchronized(lock) {
            val currentSource = source
            if (currentSource == null || state.isChanging) return@synchronized null
            operationRevision += 1
            updateStateLocked(
                state.copy(
                    isServiceConnected = true,
                    isLoading = true,
                    hasError = false,
                ),
            )
            ReadTask(operationRevision, bindingRevision, currentSource)
        }
        task?.let(::executeRead)
    }

    fun setGranted(granted: Boolean): Boolean {
        val task = synchronized(lock) {
            val currentSource = source
            if (
                currentSource == null ||
                !state.writable ||
                state.isGranted == granted
            ) {
                return false
            }
            operationRevision += 1
            val task = ChangeTask(
                operationRevision,
                bindingRevision,
                currentSource,
                granted,
            )
            updateStateLocked(
                state.copy(
                    isChanging = true,
                    hasError = false,
                ),
            )
            task
        }
        workerExecutor.execute { executeChange(task) }
        return true
    }

    fun addListener(listener: ScopeSettingsListener) {
        listeners.add(listener)
        val snapshot = state
        mainExecutor.execute {
            if (listeners.contains(listener)) notifyListener(listener, snapshot)
        }
    }

    fun removeListener(listener: ScopeSettingsListener) {
        listeners.remove(listener)
    }

    internal fun currentState(): ScopeUiState = state

    private fun executeRead(task: ReadTask) {
        workerExecutor.execute {
            if (!isCurrent(task)) return@execute
            val granted = try {
                task.source.getScope().contains(TARGET_PACKAGE)
            } catch (_: RuntimeException) {
                null
            }
            synchronized(lock) {
                if (!isCurrentLocked(task)) return@synchronized
                updateStateLocked(
                    state.copy(
                        isServiceConnected = true,
                        isLoading = false,
                        isChanging = false,
                        isGranted = granted == true,
                        hasError = granted == null,
                    ),
                )
            }
        }
    }

    private fun executeChange(task: ChangeTask) {
        if (!isCurrent(task)) return
        try {
            if (task.granted) {
                task.source.requestScope(
                    listOf(TARGET_PACKAGE),
                    object : ScopeRequestCallback {
                        override fun onApproved(packages: List<String>) {
                            workerExecutor.execute { finishChangeByRead(task) }
                        }

                        override fun onFailed() {
                            workerExecutor.execute { finishChangeFailure(task) }
                        }
                    },
                )
            } else {
                task.source.removeScope(listOf(TARGET_PACKAGE))
                finishChangeByRead(task)
            }
        } catch (_: RuntimeException) {
            finishChangeFailure(task)
        }
    }

    private fun finishChangeByRead(task: ChangeTask) {
        if (!isCurrent(task)) return
        val actual = try {
            task.source.getScope().contains(TARGET_PACKAGE)
        } catch (_: RuntimeException) {
            null
        }
        synchronized(lock) {
            if (!isCurrentLocked(task)) return@synchronized
            updateStateLocked(
                state.copy(
                    isServiceConnected = true,
                    isLoading = false,
                    isChanging = false,
                    isGranted = actual == true,
                    hasError = actual == null || actual != task.granted,
                ),
            )
        }
    }

    private fun finishChangeFailure(task: ChangeTask) {
        synchronized(lock) {
            if (!isCurrentLocked(task)) return@synchronized
            updateStateLocked(
                state.copy(
                    isServiceConnected = true,
                    isLoading = false,
                    isChanging = false,
                    hasError = true,
                ),
            )
        }
    }

    private fun isCurrent(task: ReadTask): Boolean = synchronized(lock) {
        isCurrentLocked(task)
    }

    private fun isCurrent(task: ChangeTask): Boolean = synchronized(lock) {
        isCurrentLocked(task)
    }

    private fun isCurrentLocked(task: ReadTask): Boolean =
        operationRevision == task.operationRevision &&
            bindingRevision == task.bindingRevision &&
            source === task.source

    private fun isCurrentLocked(task: ChangeTask): Boolean =
        operationRevision == task.operationRevision &&
            bindingRevision == task.bindingRevision &&
            source === task.source

    private fun updateStateLocked(newState: ScopeUiState) {
        state = newState
        mainExecutor.execute {
            listeners.forEach { listener -> notifyListener(listener, newState) }
        }
    }

    private fun notifyListener(listener: ScopeSettingsListener, newState: ScopeUiState) {
        try {
            listener.onScopeStateChanged(newState)
        } catch (_: RuntimeException) {
            Log.w(TAG, "Scope settings listener failed")
        }
    }

    private data class ReadTask(
        val operationRevision: Long,
        val bindingRevision: Long,
        val source: ScopeService,
    )

    private data class ChangeTask(
        val operationRevision: Long,
        val bindingRevision: Long,
        val source: ScopeService,
        val granted: Boolean,
    )

    private companion object {
        private const val TAG = "HuaweiTrim"
        private const val TARGET_PACKAGE = HookInstallPolicy.TARGET_PACKAGE
    }
}
