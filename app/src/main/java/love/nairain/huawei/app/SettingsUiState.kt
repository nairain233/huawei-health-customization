package love.nairain.huawei.app

import love.nairain.huawei.config.SettingsCatalog

internal enum class SettingsNoticeKind {
    LOADING,
    SAVING,
    RESTART_REQUIRED,
    DEFAULTS_NOT_PERSISTED,
    SAVE_FAILED,
    STATE_UNCERTAIN,
    CONFIG_UNAVAILABLE,
}

internal data class SettingsNotice(val kind: SettingsNoticeKind)

internal data class SettingsUiState(
    val revision: Long = 0,
    val isServiceConnected: Boolean = false,
    val isConfigAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val confirmedValues: Map<String, Boolean> = SettingsCatalog.defaults,
    val notice: SettingsNotice? = null,
) {
    val writable: Boolean
        get() = isServiceConnected && isConfigAvailable && !isLoading && !isSaving

    fun valueOf(key: String): Boolean = confirmedValues[key] ?: SettingsCatalog.defaults[key] ?: false
}
