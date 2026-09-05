package love.nairain.huawei.hook

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.libxposed.api.XposedInterface
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.feature.BottomTabFeature
import love.nairain.huawei.hook.feature.DevicePageFeature
import love.nairain.huawei.hook.feature.HealthPageFeature
import love.nairain.huawei.hook.feature.MinePageFeature
import love.nairain.huawei.hook.feature.SportPageFeature
import love.nairain.huawei.hook.symbols.HuaweiHealthHookPoints
import love.nairain.huawei.hook.util.ModuleLogger

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
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.packageManager.getPackageInfo(
                    application.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                application.packageManager.getPackageInfo(application.packageName, 0)
            }
        }.getOrNull()
        val versionName = packageInfo?.versionName
        val versionCode = packageInfo?.longVersionCode ?: 0L
        if (!HookInstallPolicy.acceptsVersion(versionName, versionCode)) {
            logger.warn("Unsupported Huawei Health version; hooks skipped")
            return
        }
        val points = HuaweiHealthHookPoints.V17_0_7_310
        if (!points.isComplete()) {
            logger.warn("Huawei Health symbol table incomplete; hooks skipped")
            return
        }
        val config = readConfig(framework, logger)
        val context = HookContext(
            framework = framework,
            classLoader = classLoader,
            application = application,
            versionName = versionName!!,
            versionCode = versionCode,
            config = config,
            points = points,
            logger = logger,
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

    private fun readConfig(
        framework: XposedInterface,
        logger: ModuleLogger,
    ): Map<String, Boolean> = try {
        SettingsCatalog.read(framework.getRemotePreferences(SettingsKeys.GROUP))
    } catch (error: RuntimeException) {
        logger.warn("Remote configuration unavailable; safe defaults used", error)
        SettingsCatalog.defaults
    }
}
