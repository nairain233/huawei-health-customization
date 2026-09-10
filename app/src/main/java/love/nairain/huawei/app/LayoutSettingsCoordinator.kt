package love.nairain.huawei.app

import android.content.SharedPreferences
import android.util.Log
import love.nairain.huawei.config.LayoutConfigStore
import love.nairain.huawei.config.LayoutWriteOutcome
import love.nairain.huawei.config.SettingsCatalog
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor

internal fun interface LayoutPreferencesSource {
    fun getPreferences(): SharedPreferences
}

internal fun interface LayoutSettingsListener {
    fun onSettingsChanged(state: SettingsUiState)
}

/** 应用级布局配置协调器：串行执行远程读写，只向界面发布已确认值。 */
internal class LayoutSettingsCoordinator(
    private val workerExecutor: Executor,
    private val mainExecutor: Executor,
    private val store: LayoutConfigStore = LayoutConfigStore(),
) {
    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<LayoutSettingsListener>()
    private var source: LayoutPreferencesSource? = null
    private var bindingRevision = 0L
    private var revision = 0L
    private var saveInFlight = false
    private var uncertainBindingRevision: Long? = null

    @Volatile
    private var state = SettingsUiState()

    fun bind(newSource: LayoutPreferencesSource?) {
        val task = synchronized(lock) {
            revision += 1
            bindingRevision += 1
            source = newSource
            saveInFlight = false
            uncertainBindingRevision = null
            if (newSource == null) {
                updateStateLocked(
                    state.copy(
                        revision = revision,
                        isServiceConnected = false,
                        isConfigAvailable = false,
                        isLoading = false,
                        isSaving = false,
                        notice = null,
                    ),
                )
                null
            } else {
                val task = ReadTask(revision, bindingRevision, newSource)
                updateStateLocked(
                    state.copy(
                        revision = revision,
                        isServiceConnected = true,
                        isConfigAvailable = false,
                        isLoading = true,
                        isSaving = false,
                        notice = SettingsNotice(SettingsNoticeKind.LOADING),
                    ),
                )
                task
            }
        }
        task?.let(::executeRead)
    }

    fun refresh() {
        val task = synchronized(lock) {
            revision += 1
            val currentSource = source
            when {
                currentSource == null -> {
                    updateStateLocked(state.copy(revision = revision))
                    null
                }
                uncertainBindingRevision == bindingRevision -> {
                    updateStateLocked(
                        state.copy(
                            revision = revision,
                            isConfigAvailable = false,
                            isLoading = false,
                            isSaving = false,
                            notice = SettingsNotice(SettingsNoticeKind.STATE_UNCERTAIN),
                        ),
                    )
                    null
                }
                else -> {
                    val task = ReadTask(revision, bindingRevision, currentSource)
                    updateStateLocked(
                        state.copy(
                            revision = revision,
                            isLoading = true,
                            isSaving = saveInFlight,
                            notice = SettingsNotice(
                                if (saveInFlight) SettingsNoticeKind.SAVING else SettingsNoticeKind.LOADING,
                            ),
                        ),
                    )
                    task
                }
            }
        }
        task?.let(::executeRead)
    }

    fun save(key: String, value: Boolean): Boolean {
        val task = synchronized(lock) {
            val currentSource = source
            if (
                currentSource == null ||
                !state.writable ||
                saveInFlight ||
                !SettingsCatalog.defaults.containsKey(key) ||
                state.valueOf(key) == value ||
                SettingsCatalog.normalizeWrite(key, value, state.confirmedValues) ==
                SettingsCatalog.normalize(state.confirmedValues)
            ) {
                return false
            }
            revision += 1
            saveInFlight = true
            val task = SaveTask(
                revision = revision,
                bindingRevision = bindingRevision,
                source = currentSource,
                previousValues = state.confirmedValues,
                key = key,
                value = value,
            )
            updateStateLocked(
                state.copy(
                    revision = revision,
                    isLoading = false,
                    isSaving = true,
                    notice = SettingsNotice(SettingsNoticeKind.SAVING),
                ),
            )
            task
        }
        executeSave(task)
        return true
    }

    fun addListener(listener: LayoutSettingsListener) {
        listeners.add(listener)
        val snapshot = state
        mainExecutor.execute {
            if (listeners.contains(listener)) notifyListener(listener, snapshot)
        }
    }

    fun removeListener(listener: LayoutSettingsListener) {
        listeners.remove(listener)
    }

    internal fun currentState(): SettingsUiState = state

    private fun executeRead(task: ReadTask) {
        workerExecutor.execute {
            if (!isCurrentRead(task)) return@execute
            val result = try {
                store.load(task.source.getPreferences())
            } catch (_: RuntimeException) {
                null
            }
            synchronized(lock) {
                if (!isCurrentReadLocked(task)) return@synchronized
                when (result?.defaultWriteOutcome) {
                    LayoutWriteOutcome.SUCCESS -> updateStateLocked(
                        state.copy(
                            revision = task.revision,
                            isServiceConnected = true,
                            isConfigAvailable = true,
                            isLoading = false,
                            isSaving = false,
                            confirmedValues = result.values,
                            notice = null,
                        ),
                    )
                    LayoutWriteOutcome.FAILED_RESTORED -> updateStateLocked(
                        state.copy(
                            revision = task.revision,
                            isServiceConnected = true,
                            isConfigAvailable = true,
                            isLoading = false,
                            isSaving = false,
                            confirmedValues = result.values,
                            notice = SettingsNotice(SettingsNoticeKind.DEFAULTS_NOT_PERSISTED),
                        ),
                    )
                    LayoutWriteOutcome.FAILED_UNCERTAIN -> markUncertainLocked(task.bindingRevision)
                    null -> updateStateLocked(
                        state.copy(
                            revision = task.revision,
                            isServiceConnected = true,
                            isConfigAvailable = false,
                            isLoading = false,
                            isSaving = false,
                            notice = SettingsNotice(SettingsNoticeKind.CONFIG_UNAVAILABLE),
                        ),
                    )
                }
            }
        }
    }

    private fun executeSave(task: SaveTask) {
        workerExecutor.execute {
            if (!isCurrentBinding(task.bindingRevision, task.source)) return@execute
            val result = try {
                store.save(task.source.getPreferences(), task.previousValues, task.key, task.value)
            } catch (_: RuntimeException) {
                null
            }
            synchronized(lock) {
                if (!isCurrentBindingLocked(task.bindingRevision, task.source)) return@synchronized
                saveInFlight = false
                if (
                    result?.outcome != LayoutWriteOutcome.FAILED_UNCERTAIN &&
                    revision != task.revision
                ) {
                    return@synchronized
                }
                when (result?.outcome) {
                    LayoutWriteOutcome.SUCCESS -> updateStateLocked(
                        state.copy(
                            isConfigAvailable = true,
                            isLoading = false,
                            isSaving = false,
                            confirmedValues = result.values,
                            notice = if (result.changed) {
                                SettingsNotice(SettingsNoticeKind.RESTART_REQUIRED)
                            } else {
                                state.notice
                            },
                        ),
                    )
                    LayoutWriteOutcome.FAILED_RESTORED -> updateStateLocked(
                        state.copy(
                            isConfigAvailable = true,
                            isLoading = false,
                            isSaving = false,
                            confirmedValues = task.previousValues,
                            notice = SettingsNotice(SettingsNoticeKind.SAVE_FAILED),
                        ),
                    )
                    LayoutWriteOutcome.FAILED_UNCERTAIN -> markUncertainLocked(task.bindingRevision)
                    null -> updateStateLocked(
                        state.copy(
                            isConfigAvailable = false,
                            isLoading = false,
                            isSaving = false,
                            confirmedValues = task.previousValues,
                            notice = SettingsNotice(SettingsNoticeKind.CONFIG_UNAVAILABLE),
                        ),
                    )
                }
            }
        }
    }

    private fun markUncertainLocked(taskBindingRevision: Long) {
        if (bindingRevision != taskBindingRevision) return
        uncertainBindingRevision = taskBindingRevision
        saveInFlight = false
        updateStateLocked(
            state.copy(
                isConfigAvailable = false,
                isLoading = false,
                isSaving = false,
                notice = SettingsNotice(SettingsNoticeKind.STATE_UNCERTAIN),
            ),
        )
    }

    private fun isCurrentRead(task: ReadTask): Boolean = synchronized(lock) { isCurrentReadLocked(task) }

    private fun isCurrentReadLocked(task: ReadTask): Boolean =
        revision == task.revision &&
            isCurrentBindingLocked(task.bindingRevision, task.source) &&
            uncertainBindingRevision != task.bindingRevision

    private fun isCurrentBinding(taskBindingRevision: Long, taskSource: LayoutPreferencesSource): Boolean =
        synchronized(lock) { isCurrentBindingLocked(taskBindingRevision, taskSource) }

    private fun isCurrentBindingLocked(
        taskBindingRevision: Long,
        taskSource: LayoutPreferencesSource,
    ): Boolean = bindingRevision == taskBindingRevision && source === taskSource

    private fun updateStateLocked(newState: SettingsUiState) {
        state = newState
        mainExecutor.execute {
            listeners.forEach { listener -> notifyListener(listener, newState) }
        }
    }

    private fun notifyListener(listener: LayoutSettingsListener, newState: SettingsUiState) {
        try {
            listener.onSettingsChanged(newState)
        } catch (_: RuntimeException) {
            Log.w(TAG, "Layout settings listener failed")
        }
    }

    private data class ReadTask(
        val revision: Long,
        val bindingRevision: Long,
        val source: LayoutPreferencesSource,
    )

    private data class SaveTask(
        val revision: Long,
        val bindingRevision: Long,
        val source: LayoutPreferencesSource,
        val previousValues: Map<String, Boolean>,
        val key: String,
        val value: Boolean,
    )

    private companion object {
        private const val TAG = "HuaweiTrim"
    }
}
