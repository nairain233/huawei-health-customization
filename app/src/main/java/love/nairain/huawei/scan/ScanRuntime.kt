package love.nairain.huawei.scan

import android.app.Application
import android.content.pm.PackageInfo
import android.os.SystemClock
import android.util.AtomicFile
import love.nairain.huawei.hook.util.ModuleLogger
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import org.luckypray.dexkit.wrap.DexClass
import java.io.File
import java.util.UUID

/** 全部磁盘、Dex 与宿主本地报告均由一次性工作线程执行。 */
internal object ScanRuntime {
    private fun verify(descriptor: String, loader: ClassLoader) {
        when {
            "(" in descriptor -> DexMethod(descriptor).let {
                if (it.isConstructor) it.getConstructorInstance(loader) else it.getMethodInstance(loader)
            }
            "->" in descriptor -> DexField(descriptor).getFieldInstance(loader)
            else -> DexClass(descriptor).getInstance(loader)
        }
    }
    fun start(app: Application, info: PackageInfo, loader: ClassLoader, request: String, service: String,
              logger: ModuleLogger, dex: HostDexSession, install: (LayoutResolution, String) -> Unit) {
        Thread({
            try { execute(app, info, loader, request, service, logger, dex, install) }
            catch (error: Throwable) { logger.warn("Scan worker unavailable: ${error.javaClass.simpleName}") }
            finally { dex.finishLayout() }
        }, "huawei-dex-scan").start()
    }

    private fun execute(app: Application, info: PackageInfo, loader: ClassLoader, request: String, service: String,
                        logger: ModuleLogger, dex: HostDexSession, install: (LayoutResolution, String) -> Unit) {
        val dir = love.nairain.huawei.settings.HostStorage.scanDirectory(app)
        var report = ScanReport("pending", info.versionName.orEmpty(), info.longVersionCode, info.lastUpdateTime,
            request, UUID.randomUUID().toString(), System.currentTimeMillis(), service = service)
        val start = SystemClock.elapsedRealtime()
        fun publish() {
            runCatching {
                write(File(dir, "report.json"), report.encode())
            }.onFailure { logger.warn("Scan report persistence deferred: ${it.javaClass.simpleName}") }

        }
        try {
            check(dir.isDirectory || dir.mkdirs())
            publish()
            val paths = listOf(app.applicationInfo.sourceDir) + app.applicationInfo.splitSourceDirs.orEmpty().sorted()
            val identity = ApkIdentity.fingerprint(info.versionName.orEmpty(), info.longVersionCode, info.lastUpdateTime, paths.map(::File))
            report = report.copy(identity = identity, sequence = report.sequence + 1, time = System.currentTimeMillis())
            publish()
            val cache = File(dir, "resolution.json")
            val hostResources = HostResources(app.resources, loader, app.packageName)
            val cached = runCatching {
                ScanCache.decode(AtomicFile(cache).openRead().bufferedReader().use { it.readText() }, identity, request) { verify(it, loader) }.also { resolution ->
                    ScanCache.verifyResources(resolution, hostResources::id)
                }
            }.getOrNull()
            val result = cached ?: dex.layout { bridges ->
                LayoutScanner(bridges, hostResources::id,
                    { verify(it, loader) }).scan { checked, matched ->
                    report = report.copy(checked = checked, matched = matched, sequence = report.sequence + 1, time = System.currentTimeMillis())
                    publish()
                }
            }
            val complete = "error" !in result.failures.values
            val cacheSaved = complete && runCatching { write(cache, ScanCache.encode(identity, request, result)) }
                .onFailure { logger.warn("Scan cache unavailable: ${it.javaClass.simpleName}") }.isSuccess
            report = report.copy(phase = if (cacheSaved) "complete" else "failed",
                error = if (cacheSaved) "" else if (complete) "cache_error" else "scan_error", checked = ScanProtocol.keys, matched = result.matched,
                sequence = report.sequence + 1, time = System.currentTimeMillis())
            publish()
            logger.info("Scan ${if (cached == null) "finished" else "cached"}: ${result.matched.size}/${ScanProtocol.keys.size}, ${SystemClock.elapsedRealtime() - start}ms")
            result.issues.forEach { (role, reason) -> logger.info("Scan source kept: $role, $reason") }
            result.failures.forEach { (key, reason) -> logger.info("Scan kept: $key, $reason") }
            try { install(result, identity) } catch (error: Throwable) {
                logger.warn("Layout installation skipped: ${error.javaClass.simpleName}")
            }
        } catch (error: Throwable) {
            report = report.copy(phase = "failed", error = "scan_error", sequence = report.sequence + 1, time = System.currentTimeMillis())
            publish()
            logger.warn("Scan skipped: ${error.javaClass.simpleName}")
        }
    }

    private fun write(file: File, content: String) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(content.toByteArray()); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
}
