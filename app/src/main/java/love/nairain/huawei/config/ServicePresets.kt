package love.nairain.huawei.config

/** 仅收录 APK 17.0.7.310 中有明确职责证据的服务。 */
internal enum class ServicePreset(
    val id: String,
    val components: Set<String>,
) {
    APP_UPDATE("app_update", setOf(
        "com.huawei.health/com.huawei.hwversionmgr.utils.service.UpdateService",
        "com.huawei.health/com.huawei.hwdevice.mainprocess.mgr.hwotamanager.HwUpdateService",
        "com.huawei.health/com.huawei.hwversionmgr.utils.service.ScaleUpdateService",
    )),
    MUSIC_SYNC("music_sync", setOf(
        "com.huawei.health/com.huawei.hwdevice.mainprocess.service.SyncMusicService",
    )),
    TRAINING_PLAN("training_plan", setOf(
        "com.huawei.health/com.huawei.health.suggestion.service.PlanSendService",
    )),
    WATCH_FACE_TRIAL("watch_face_trial", setOf(
        "com.huawei.health/com.huawei.watchface.mvp.ui.service.TryOutWatchFaceService",
    ));

    companion object {
        fun fromId(id: String): ServicePreset? = entries.firstOrNull { it.id == id }
    }
}
