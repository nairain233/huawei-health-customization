package love.nairain.huawei.settings

import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServicePreset
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.config.SportSimplificationConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 仅属于宿主的新配置，不读取或迁移任何模块偏好。 */
internal data class HostConfiguration(
    val layout: Map<String, Boolean> = SettingsCatalog.defaults,
    val services: ServiceBlockConfig = ServiceBlockConfig(),
    val scanRequest: String = "",
    val sport: SportSimplificationConfig = SportSimplificationConfig(),
) {
    fun normalized(): HostConfiguration {
        require(services.components.all { ServiceBlockConfig.normalize(it) == it })
        require(services.presets.all(ServicePreset::isKnown))
        require(scanRequest.isEmpty() || UUID.fromString(scanRequest).toString() == scanRequest)
        require(SportSimplificationConfig.decode(sport.encode()) == sport)
        return copy(layout = SettingsCatalog.normalize(layout), services = services.copy(
            components = services.components.toSet(), presets = services.presets.toSet()))
    }
    fun encode(): String {
        val value = normalized()
        return JSONObject().put(SettingsKeys.SCHEMA_VERSION, SettingsKeys.CURRENT_SCHEMA_VERSION)
            .put("layout", JSONObject(value.layout))
            .put("services", JSONObject().put(ServiceBlockConfig.ENABLED, value.services.enabled).put(ServiceBlockConfig.DEBUG_MODE, value.services.debugMode)
                .put(ServiceBlockConfig.COMPONENTS, JSONArray(value.services.components.sorted())).put(ServiceBlockConfig.PRESETS, JSONArray(value.services.presets.sorted())))
            .put("sport", value.sport.encode()).put("scan.request", value.scanRequest).toString()
    }
    companion object {
        const val MAX_CHARS = 131072
        fun decode(raw: String): HostConfiguration {
            require(raw.length <= MAX_CHARS)
            val json = JSONObject(raw)
            val schema = json.getInt(SettingsKeys.SCHEMA_VERSION)
            require(schema == 3 || schema == SettingsKeys.CURRENT_SCHEMA_VERSION)
            val layout = json.getJSONObject("layout")
            val keys = layout.keys().asSequence().toSet()
            if (schema == 3) require(SettingsKeys.ENABLED in keys && SettingsCatalog.defaults.keys.containsAll(keys))
            else require(keys == SettingsCatalog.defaults.keys)
            val values = SettingsCatalog.defaults.mapValues { (key, default) ->
                if (key !in keys) default else {
                    require(layout.get(key) is Boolean); layout.getBoolean(key)
                }
            }
            val service = json.getJSONObject("services")
            require(service.get(ServiceBlockConfig.ENABLED) is Boolean && service.get(ServiceBlockConfig.DEBUG_MODE) is Boolean)
            fun strings(key: String): Set<String> {
                val array = service.getJSONArray(key)
                require(array.length() <= 1024)
                return (0 until array.length()).map { require(array.get(it) is String); array.getString(it) }.toSet()
            }
            require(json.get("scan.request") is String)
            return HostConfiguration(values, ServiceBlockConfig(service.getBoolean(ServiceBlockConfig.ENABLED), strings(ServiceBlockConfig.COMPONENTS),
                strings(ServiceBlockConfig.PRESETS), service.getBoolean(ServiceBlockConfig.DEBUG_MODE)), json.getString("scan.request"),
                if (schema == 3) SportSimplificationConfig() else SportSimplificationConfig.decode(json.getJSONObject("sport"))).normalized()
        }
    }
}

internal interface HostConfigStorage {
    fun read(): String?
    fun write(raw: String)
}

internal enum class HostSaveResult { SAVED, RESTORED, UNCERTAIN, UNAVAILABLE }

/** 只由宿主串行执行器访问；确认落盘后才替换快照。 */
internal class HostConfigStore(private val storage: HostConfigStorage) {
    var confirmed: HostConfiguration? = null
        private set
    var writable = false
        private set

    fun load(): HostConfiguration {
        writable = false
        val raw = storage.read()
        val loaded = if (raw == null) HostConfiguration().also {
            storage.write(it.encode())
            check(HostConfiguration.decode(requireNotNull(storage.read())) == it)
        } else HostConfiguration.decode(raw)
        confirmed = loaded
        writable = true
        return loaded
    }

    fun save(next: HostConfiguration): HostSaveResult {
        val previous = confirmed ?: return HostSaveResult.UNAVAILABLE
        if (!writable) return HostSaveResult.UNAVAILABLE
        val normalized = next.normalized()
        if (normalized == previous) return HostSaveResult.SAVED
        val previousRaw = try {
            requireNotNull(storage.read()).also { check(HostConfiguration.decode(it) == previous) }
        } catch (_: Exception) { writable = false; return HostSaveResult.UNAVAILABLE }
        try {
            storage.write(normalized.encode())
            check(HostConfiguration.decode(requireNotNull(storage.read())) == normalized)
            confirmed = normalized
            return HostSaveResult.SAVED
        } catch (_: Exception) {
            val restored = runCatching {
                if (storage.read() != previousRaw) storage.write(previousRaw)
                check(storage.read() == previousRaw)
            }.isSuccess
            writable = restored
            return if (restored) HostSaveResult.RESTORED else HostSaveResult.UNCERTAIN
        }
    }
}
