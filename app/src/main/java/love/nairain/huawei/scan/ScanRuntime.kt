package love.nairain.huawei.scan

import android.app.Application
import android.content.pm.PackageInfo
import android.os.SystemClock
import android.util.AtomicFile
import androidx.core.net.toUri
import love.nairain.huawei.hook.HookInstallPolicy
import love.nairain.huawei.hook.util.ModuleLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import org.luckypray.dexkit.wrap.DexClass
import java.io.File
import java.util.UUID

/** 全部磁盘、Dex 与报告 IPC 均由一次性工作线程执行。 */
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
              logger: ModuleLogger, install: (LayoutResolution) -> Unit) {
        Thread({
            try { execute(app, info, loader, request, service, logger, install) }
            catch (error: Throwable) { logger.warn("Scan worker unavailable: ${error.javaClass.simpleName}") }
        }, "huawei-dex-scan").start()
    }

    private fun execute(app: Application, info: PackageInfo, loader: ClassLoader, request: String, service: String,
                        logger: ModuleLogger, install: (LayoutResolution) -> Unit) {
        val dir = File(app.filesDir, "huawei_trim_scan")
        var report = ScanReport("pending", info.versionName.orEmpty(), info.longVersionCode, info.lastUpdateTime,
            request, UUID.randomUUID().toString(), System.currentTimeMillis(), service = service)
        val start = SystemClock.elapsedRealtime()
        fun publish() {
            runCatching {
                write(File(dir, "report.json"), report.encode())
                app.contentResolver.call("content://${ScanProtocol.AUTHORITY}".toUri(), "report", report.encode(), null)
            }.onFailure { logger.warn("Scan report deferred: ${it.javaClass.simpleName}") }
        }
        try {
            check(dir.isDirectory || dir.mkdirs())
            val oldReport = File(dir, "report.json")
            if (oldReport.isFile) runCatching {
                val pending = oldReport.readText()
                ScanReport.decode(pending)
                app.contentResolver.call("content://${ScanProtocol.AUTHORITY}".toUri(), "report", pending, null)
            }.onFailure { logger.warn("Previous scan report unavailable: ${it.javaClass.simpleName}") }
            publish()
            val paths = listOf(app.applicationInfo.sourceDir) + app.applicationInfo.splitSourceDirs.orEmpty().sorted()
            val identity = ApkIdentity.fingerprint(info.versionName.orEmpty(), info.longVersionCode, info.lastUpdateTime, paths.map(::File))
            report = report.copy(identity = identity, sequence = report.sequence + 1, time = System.currentTimeMillis())
            publish()
            val cache = File(dir, "resolution.json")
            val cached = runCatching {
                ScanCache.decode(AtomicFile(cache).openRead().bufferedReader().use { it.readText() }, identity, request) { verify(it, loader) }
            }.getOrNull()
            val result = cached ?: run {
                System.loadLibrary("dexkit")
                val bridges = mutableListOf<DexKitBridge>()
                try {
                    paths.forEach { bridges += DexKitBridge.create(it) }
                    LayoutScanner(bridges, HostResources(app.resources, loader, app.packageName)::id,
                        { verify(it, loader) }, HookInstallPolicy.acceptsVersion(info.versionName, info.longVersionCode)).scan { checked, matched ->
                        report = report.copy(checked = checked, matched = matched, sequence = report.sequence + 1, time = System.currentTimeMillis())
                        publish()
                    }
                } finally { bridges.forEach { it.close() } }
            }
            val complete = "error" !in result.failures.values
            val cacheSaved = complete && runCatching { write(cache, ScanCache.encode(identity, request, result)) }
                .onFailure { logger.warn("Scan cache unavailable: ${it.javaClass.simpleName}") }.isSuccess
            report = report.copy(phase = if (cacheSaved) "complete" else "failed",
                error = if (cacheSaved) "" else if (complete) "cache_error" else "scan_error", checked = ScanProtocol.keys, matched = result.matched,
                sequence = report.sequence + 1, time = System.currentTimeMillis())
            publish()
            logger.info("Scan ${if (cached == null) "finished" else "cached"}: ${result.matched.size}/${ScanProtocol.keys.size}, ${SystemClock.elapsedRealtime() - start}ms")
            result.failures.forEach { (key, reason) -> logger.info("Scan kept: $key, $reason") }
            try { install(result) } catch (error: Throwable) {
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
