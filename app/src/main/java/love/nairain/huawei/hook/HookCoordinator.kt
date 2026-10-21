package love.nairain.huawei.hook

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.hook.feature.ServiceBlockFeature
import love.nairain.huawei.hook.feature.BottomTabFeature
import love.nairain.huawei.hook.feature.DevicePageFeature
import love.nairain.huawei.hook.feature.HealthPageFeature
import love.nairain.huawei.hook.feature.MinePageFeature
import love.nairain.huawei.hook.feature.SportPageFeature
import love.nairain.huawei.hook.symbols.HuaweiHealthHookPoints
import love.nairain.huawei.hook.util.ModuleLogger
import love.nairain.huawei.scan.ScanRuntime
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.hook.util.ActivePageObserver

/** 只安装 Application.attach；真实版本与配置均在 attach 完成后读取一次。 */
internal object HookCoordinator {
    private val installGate = HookInstallGate()

    @SuppressLint("DiscouragedPrivateApi") // Hook 必须在目标应用的 Context 初始化后安装。
    fun installAttachHook(framework: XposedInterface, classLoader: ClassLoader) {
        if (!installGate.claimAttach()) return
        val logger = ModuleLogger(framework)
        val attach = runCatching {
            Application::class.java.getDeclaredMethod("attach", Context::class.java).apply {
                isAccessible = true
            }
        }.getOrElse {
            logger.warn("Application.attach unavailable", it)
            return
        }
        framework.hook(attach)
            .setId("huawei.application.attach")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val application = chain.thisObject as? Application
                if (application != null && installGate.claimRuntime()) {
                    installAfterAttach(framework, classLoader, application, logger)
                }
                result
            }
    }

    private fun installAfterAttach(
        framework: XposedInterface,
        classLoader: ClassLoader,
        application: Application,
        logger: ModuleLogger,
    ) {
        if (application.packageName != HookInstallPolicy.TARGET_PACKAGE) return
        ActivePageObserver.register(application)
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.packageManager.getPackageInfo(
                    application.packageName,
                    PackageManager.PackageInfoFlags.of((PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                application.packageManager.getPackageInfo(application.packageName, PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS)
            }
        }.getOrNull()
        val versionName = packageInfo?.versionName
        val versionCode = packageInfo?.longVersionCode ?: 0L
        if (packageInfo == null) {
            logger.warn("Huawei Health package metadata unavailable; hooks skipped")
            return
        }
        if (!love.nairain.huawei.hook.resolver.HostSettingsTargets.accepts(versionName, versionCode)) {
            logger.info("Host version unsupported; original interface retained")
            return
        }
        val client = love.nairain.huawei.settings.HostSettingsController(application) { logger.warn(it) }
        val restarter = love.nairain.huawei.settings.HostRestarter.create(application, { client.canRestart }) { logger.warn(it) }
        love.nairain.huawei.hook.feature.HostSettingsFeature.install(
            framework, classLoader, application, versionName, versionCode, logger, client, restarter,
        )
        client.initialize { startup ->
            val serviceConfig = startup.services
            val declaredServices = packageInfo.services.orEmpty().mapNotNull {
                ServiceBlockConfig.componentName(it.packageName, it.name)
            }.toSet()
            val serviceStatus = ServiceBlockFeature.install(
                framework,
                serviceConfig.copy(components = serviceConfig.effectiveComponents(declaredServices)),
                declaredServices,
                logger,
            )
            val config = startup.layout
            love.nairain.huawei.hook.feature.LocalWatchFaceFeature.install(
                framework, classLoader, application, versionName, versionCode,
                config[SettingsKeys.LOCAL_WATCH_FACE] == true,
                serviceConfig.effectiveComponents(declaredServices).any {
                    it in love.nairain.huawei.config.ServicePreset.DEVICE_CORE.components
                }, logger, restarter,
            )
            ScanRuntime.start(application, packageInfo, classLoader, startup.scanRequest, serviceStatus, logger) { resolution, identity ->
                val points = HuaweiHealthHookPoints.ANCHORS.copy(
                    mineListManager = resolution.mineManager,
                )
                client.identifySport(identity)
                val context = HookContext(
                    framework = framework,
                    classLoader = classLoader,
                    application = application,
                    versionName = versionName.orEmpty(),
                    versionCode = versionCode,
                    config = config.mapValues { (key, value) -> value && (!key.startsWith("hide.") || key in resolution.matched) },
                    points = points,
                    logger = logger,
                    resolvedGroups = resolution.capabilities.filter { (group, keys) -> keys.any { config[it] == true } ||
                        (group.startsWith("sport.") && config[SettingsKeys.ENABLED] == true) }.keys,
                    resolvedCapabilities = resolution.capabilities,
                    resolution = resolution,
                    sport = startup.sport,
                    apkIdentity = identity,
                    sportTabsChanged = client::sportTabsChanged,
                )
                HookRegistry(
                    listOf(
                        HealthPageFeature(),
                        SportPageFeature(),
                        DevicePageFeature(),
                        MinePageFeature(),
                        BottomTabFeature(),
                    ),
                ).installAll(context)
            }
        }
    }
}
