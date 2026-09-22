package love.nairain.huawei.config

import love.nairain.huawei.R

internal enum class ServiceSection { LOW_COUPLING, CORE }

/** 按用户可理解的业务合并服务；职责混合的组仅作为核心开关展示。 */
internal enum class ServicePreset(
    val id: String,
    val section: ServiceSection,
    val title: Int,
    val impact: Int,
    val components: Set<String>,
) {
    APP_UPDATE("app_update_group", ServiceSection.LOW_COUPLING, R.string.service_group_app_update_group, R.string.service_group_app_update_group_impact, setOf(
        "com.huawei.health/com.huawei.hwversionmgr.utils.service.UpdateService",
        "com.huawei.health/com.huawei.android.bundlecore.update.ModuleUpdateService")),
    DEVICE_ASSIST("device_assist", ServiceSection.LOW_COUPLING, R.string.service_group_device_assist, R.string.service_group_device_assist_impact, setOf(
        "com.huawei.health/com.huawei.hwdevice.mainprocess.service.SyncMusicService",
        "com.huawei.health/com.huawei.health.suggestion.service.PlanSendService",
        "com.huawei.health/com.huawei.watchface.mvp.ui.service.TryOutWatchFaceService")),
    SPORT_ASSIST("sport_assist", ServiceSection.LOW_COUPLING, R.string.service_group_sport_assist, R.string.service_group_sport_assist_impact, setOf(
        "com.huawei.health/com.huawei.healthcloud.plugintrack.offlinemap.manager.service.OfflineMapService",
        "com.huawei.health/com.huawei.healthcloud.plugintrack.manager.service.VoiceEngService")),
    DEVICE_CORE("device_core", ServiceSection.CORE, R.string.service_group_device_core, R.string.service_group_device_core_impact, setOf(
        "com.huawei.health/com.huawei.hwservicesmgr.PhoneService",
        "com.huawei.health/com.huawei.hwservicesmgr.ExternalPhoneService",
        "com.huawei.health/com.huawei.hwservicesmgr.DeviceConnectionRelayService",
        "com.huawei.health/com.huawei.health.manager.reconnect.PeriodScanService",
        "com.huawei.health/com.huawei.wearengine.service.WearEngineService",
        "com.huawei.health/com.huawei.wearengine.service.WearEngineExtendService")),
    DEVICE_UPDATE("device_update", ServiceSection.CORE, R.string.service_group_device_update, R.string.service_group_device_update_impact, setOf(
        "com.huawei.health/com.huawei.hwdevice.mainprocess.mgr.hwotamanager.HwUpdateService",
        "com.huawei.health/com.huawei.hwversionmgr.utils.service.ScaleUpdateService")),
    SPORT_RECORD("sport_record", ServiceSection.CORE, R.string.service_group_sport_record, R.string.service_group_sport_record_impact, setOf(
        "com.huawei.health/com.huawei.healthcloud.plugintrack.service.KeepForegroundService",
        "com.huawei.health/com.huawei.healthcloud.plugintrack.service.KeepForegroundNoLocationService",
        "com.huawei.health/com.huawei.healthcloud.plugintrack.service.GpsKeepForegroundService",
        "com.huawei.health/com.huawei.healthcloud.plugintrack.open.TrackService")),
    HEALTH_SYNC("health_sync", ServiceSection.CORE, R.string.service_group_health_sync, R.string.service_group_health_sync_impact, setOf(
        "com.huawei.health/com.huawei.health.plan.model.ui.fitness.service.DeviceRecordSyncService",
        "com.huawei.health/com.huawei.health.plan.model.data.DataSyncService",
        "com.huawei.health/com.huawei.hihealthservice.manager.SyncCloudRemoteWorkerService",
        "com.huawei.health/com.huawei.health.manager.AccountSyncService",
        "com.huawei.health/com.huawei.health.suggestion.data.DataDownloadService")),
    MESSAGES("messages", ServiceSection.CORE, R.string.service_group_messages, R.string.service_group_messages_impact, setOf(
        "com.huawei.health/com.huawei.pluginmessagecenter.service.HuaweiHealthHmsPushService",
        "com.huawei.health/com.huawei.hms.support.api.push.service.HmsMsgService",
        "com.huawei.health/com.huawei.health.suggestion.service.PlanReportNotificationService")),
    DAEMON("daemon", ServiceSection.CORE, R.string.service_group_daemon, R.string.service_group_daemon_impact, setOf(
        "com.huawei.health/com.huawei.health.manager.DaemonService",
        "com.huawei.health/com.huawei.health.manager.PreDaemonService",
        "com.huawei.health/com.huawei.health.receiver.MainProcessHelperService")),
    WALLET("wallet", ServiceSection.CORE, R.string.service_group_wallet, R.string.service_group_wallet_impact, setOf(
        "com.huawei.health/com.huawei.health.hwwear.pluginpay.HealthWalletBusinessService",
        "com.huawei.health/com.huawei.health.hwwear.pluginpay.HealthTransitOpenService",
        "com.huawei.health/com.huawei.wear.wallet.proxy.openservice.WalletPassService",
        "com.huawei.health/com.huawei.wear.wallet.proxy.openservice.BleCarKeyService")),
    // 插件只有 Manifest 声明，尚无方法体证据；展示待确认，不生成组规则。
    SLEEP("sleep", ServiceSection.CORE, R.string.service_group_sleep, R.string.service_group_sleep_impact, emptySet()),
    DIAGNOSTICS("diagnostics", ServiceSection.CORE, R.string.service_group_diagnostics, R.string.service_group_diagnostics_impact, emptySet());

    val evidenceConfirmed: Boolean get() = components.isNotEmpty()

    companion object {
        private val appLegacy = setOf("com.huawei.health/com.huawei.hwversionmgr.utils.service.UpdateService")
        // 拆分旧更新规则时保留另一组的原始范围，不能扩大或丢失旧选择。
        private val legacy = mapOf(
            "app_update" to (appLegacy + DEVICE_UPDATE.components),
            "legacy.app_update.app" to appLegacy,
            "legacy.app_update.device" to DEVICE_UPDATE.components,
            "music_sync" to setOf("com.huawei.health/com.huawei.hwdevice.mainprocess.service.SyncMusicService"),
            "training_plan" to setOf("com.huawei.health/com.huawei.health.suggestion.service.PlanSendService"),
            "watch_face_trial" to setOf("com.huawei.health/com.huawei.watchface.mvp.ui.service.TryOutWatchFaceService"),
        )
        fun fromId(id: String): ServicePreset? = entries.firstOrNull { it.id == id }
        fun isKnown(id: String): Boolean = fromId(id) != null || id in legacy
        fun componentsFor(id: String): Set<String> = legacy[id] ?: fromId(id)?.components.orEmpty()
        fun visible(section: ServiceSection): List<ServicePreset> = entries.filter { it.section == section }
        fun selectedComponents(ids: Set<String>): Set<String> = ids.flatMap(::componentsFor).toSet()
        fun hasLegacySelection(ids: Set<String>, group: ServicePreset): Boolean =
            ids.any { it in legacy && componentsFor(it).any(group.components::contains) }

        fun changeSelection(ids: Set<String>, group: ServicePreset, selected: Boolean): Set<String> {
            if (selected && !group.evidenceConfirmed) return ids
            val expanded = if ("app_update" in ids) {
                ids - "app_update" + setOf("legacy.app_update.app", "legacy.app_update.device")
            } else ids
            val retained = expanded.filterNot { id ->
                id == group.id || (id in legacy && componentsFor(id).any(group.components::contains))
            }.toSet()
            return if (selected) retained + group.id else retained
        }
    }
}
