package love.nairain.huawei.watchface

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import love.nairain.huawei.R
import love.nairain.huawei.hook.resolver.LocalWatchFaceTargets
import love.nairain.huawei.hook.util.ModuleLogger
import org.json.JSONArray
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** 控制器仅保留 Application 与页面弱引用。所有会话状态在主线程更新，磁盘工作串行执行。 */
internal class LocalWatchFaceRuntime(
    private val app: Context,
    private val targets: LocalWatchFaceTargets,
    private val logger: ModuleLogger,
    private val serviceConflict: Boolean,
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "LocalWatchFaceIO").apply { isDaemon = true } }
    private val resources = app.createPackageContext("love.nairain.huawei", 0).resources
    private val root = File(app.filesDir, "huawei_hook_local_faces")
    private val ids = LocalFaceIds(File(root, "ids"))
    private val pages = WeakHashMap<Activity, Page>()
    private val ownedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val finishedTasks = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val localOperation = ThreadLocal<Boolean>()
    private var ready = false
    private var closed = false
    private var quarantined = false
    @Volatile private var uncertainTransfer = false
    private var pending = WeakReference<Activity>(null)
    @Volatile private var job: Job? = null
    private val manager get() = checkNotNull(targets.call("manager", null, app))
    private val bt get() = checkNotNull(targets.call("bt", null, app))
    private val config get() = checkNotNull(targets.call("config", null, app))

    private class Page(activity: Activity, bar: LinearLayout, status: TextView, action: Button,
        val frame: WeakReference<android.view.View>, val originalHeight: Int, val originalWeight: Float) {
        val activity = WeakReference(activity)
        val bar = WeakReference(bar)
        val status = WeakReference(status)
        val action = WeakReference(action)
        var dialog = WeakReference<AlertDialog>(null)
    }

    private class Job(val page: Page, val directory: File, val device: String) {
        var description: HwtArchive.Description? = null
        @Volatile var state: LocalInstallState? = null
        @Volatile var waitingList = false
        @Volatile var lockedHost = false
        @Volatile var cancelled = false
        var commandIssued = false
        var screen = ""
        var deadline = SystemClock.elapsedRealtime() + 60_000
        var started = SystemClock.elapsedRealtime()
        val payload get() = File(directory, "unpacked/${HwtArchive.PAYLOAD}")
        val taskId get() = state?.let { "${it.id}_${it.version}" }.orEmpty()
    }

    fun initialize() {
        worker.execute {
            try {
                check(root.isDirectory || root.mkdirs())
                root.listFiles().orEmpty().filter { it.name.startsWith("job-") }.forEach { HwtArchive.clean(root, it) }
                ownedIds.addAll(ids.allocated())
                post { ready = true; updateButtons() }
            } catch (error: Exception) {
                diagnostic("initialize", error)
                post { quarantined = true; pages.values.forEach { show(it, R.string.wf_restart) }; updateButtons() }
            }
        }
    }

    @android.annotation.SuppressLint("DiscouragedApi") // 资源属于已核验宿主，模块 R 无法引用；名称失配时跳过入口。
    fun attach(activity: Activity) {
        if (closed || pages.containsKey(activity)) return
        val rootId = activity.resources.getIdentifier("webview_layout", "id", app.packageName)
        val frameId = activity.resources.getIdentifier("web_view_frame_layout", "id", app.packageName)
        val parent = activity.findViewById<LinearLayout>(rootId) ?: return
        val frame = activity.findViewById<android.view.View>(frameId) ?: return
        if (frame.parent !== parent) return
        val params = frame.layoutParams as? LinearLayout.LayoutParams ?: return
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        val status = TextView(activity).apply { text = text(if (serviceConflict) R.string.wf_conflict else R.string.wf_ready) }
        if (quarantined) status.text = text(R.string.wf_restart)
        val action = Button(activity).apply { text = text(R.string.wf_import) }
        bar.addView(status)
        bar.addView(action)
        val page = Page(activity, bar, status, action, WeakReference(frame), params.height, params.weight)
        pages[activity] = page
        params.height = 0
        params.weight = 1f
        frame.layoutParams = params
        parent.addView(bar, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        action.setOnClickListener { guarded { if (job?.page === page) cancel() else choose(page) } }
        updateButtons()
    }

    fun detach(activity: Activity) {
        val page = pages.remove(activity) ?: return
        if (pending.get() === activity) pending.clear()
        if (job?.page === page) cancel()
        page.dialog.get()?.dismiss()
        page.bar.get()?.let { (it.parent as? ViewGroup)?.removeView(it) }
        page.frame.get()?.let { frame ->
            (frame.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.height = page.originalHeight; params.weight = page.originalWeight; frame.layoutParams = params
            }
        }
        updateButtons()
    }

    private fun choose(page: Page) {
        val activity = page.activity.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        if (!ready || quarantined || job != null || pending.get() != null) return
        if (!connected()) { show(page, R.string.wf_disconnected); return }
        if (targets.call("state", manager) != 0) { show(page, R.string.wf_busy); return }
        pending = WeakReference(activity)
        updateButtons()
        try {
            activity.startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, REQUEST)
        } catch (error: android.content.ActivityNotFoundException) {
            pending.clear(); show(page, R.string.wf_no_picker); updateButtons()
        } catch (error: SecurityException) {
            diagnostic("picker", error)
            pending.clear(); show(page, R.string.wf_invalid); updateButtons()
        }
    }

    fun result(activity: Activity, request: Int, result: Int, intent: Intent?): Boolean {
        if (request != REQUEST || pending.get() !== activity) return false
        pending.clear()
        val page = pages[activity] ?: return true
        val uri = intent?.data
        if (result != Activity.RESULT_OK || uri == null) { show(page, R.string.wf_cancelled); updateButtons(); return true }
        if (uri.scheme != "content") { show(page, R.string.wf_invalid); updateButtons(); return true }
        val device = device()
        if (!connected() || device.isEmpty()) { show(page, R.string.wf_disconnected); updateButtons(); return true }
        val task = Job(page, File(root, "job-${UUID.randomUUID()}"), device)
        job = task
        show(page, R.string.wf_preparing)
        updateButtons()
        tick(task)
        worker.execute {
            try {
                val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }.orEmpty()
                require(name.endsWith(".hwt", ignoreCase = true))
                check(task.directory.mkdirs())
                val archive = File(task.directory, "selected.hwt")
                checkNotNull(app.contentResolver.openInputStream(uri)).use { HwtArchive.copy(it, archive) }
                task.description = HwtArchive.extract(archive, File(task.directory, "unpacked"))
                post { if (job === task) confirm(task) }
            } catch (error: Exception) {
                diagnostic("read", error)
                post { if (job === task) finish(task, R.string.wf_invalid) }
            }
        }
        return true
    }

    private fun confirm(task: Job) {
        val activity = task.page.activity.get()?.takeUnless { it.isFinishing || it.isDestroyed }
            ?: return finish(task, R.string.wf_cancelled)
        task.deadline = Long.MAX_VALUE // 用户阅读确认框时不计安装超时。
        val dialog = AlertDialog.Builder(activity)
            .setTitle(text(R.string.wf_import)).setMessage(text(R.string.wf_confirm))
            .setPositiveButton(text(R.string.wf_install)) { _, _ -> guarded { if (job === task) begin(task) } }
            .setNegativeButton(text(R.string.wf_cancel)) { _, _ -> finish(task, R.string.wf_cancelled) }
            .setOnCancelListener { finish(task, R.string.wf_cancelled) }.create()
        task.page.dialog = WeakReference(dialog)
        dialog.show()
    }

    private fun begin(task: Job) {
        if (!connected() || device() != task.device) return finish(task, R.string.wf_disconnected)
        if (targets.call("state", manager) != 0) return finish(task, R.string.wf_busy)
        task.lockedHost = true
        task.started = SystemClock.elapsedRealtime()
        targets.call("setState", manager, 2)
        task.waitingList = true
        task.deadline = SystemClock.elapsedRealtime() + 30_000
        show(task.page, R.string.wf_wait)
        targets.call("refresh", bt)
    }

    /** 在 reportSuccessFaceInfo 入口抓取已解析完成的快照，避免把缓存当作本次确认。 */
    fun listed() {
        val task = job ?: return
        val awaiting = task.waitingList
        val observed = task.state?.phase
        if (!awaiting && observed != LocalInstallState.Phase.VERIFYING) return
        val snapshot = entries()
        post {
            if (job !== task) return@post
            if (!connected() || device() != task.device) { abort(task, -1); return@post }
            if (awaiting && task.waitingList) {
                task.waitingList = false
                prepare(task, snapshot.keys)
            } else if (task.state?.verify(snapshot, observed) == true) finish(task, R.string.wf_success)
        }
    }

    private fun prepare(task: Job, existing: Set<String>) {
        val support = targets.call("support", bt) ?: return finish(task, R.string.wf_unsupported)
        val screen = targets.call("screen", support) as? String ?: return finish(task, R.string.wf_unsupported)
        val maximum = targets.call("maxVersion", support) as? String ?: ""
        val compatible = (targets.call("compatible", support) as? List<*>).orEmpty().filterNotNull()
            .flatMap { (targets.call("compatibleVersions", it) as? String).orEmpty().split(',') } + maximum
        val signatureRequired = targets.call("signatureSupported", bt) == true
        task.screen = screen
        task.deadline = SystemClock.elapsedRealtime() + 60_000
        show(task.page, R.string.wf_signing)
        worker.execute {
            try {
                if (task.cancelled) return@execute
                val description = checkNotNull(task.description)
                val resolutions = JSONArray(app.assets.open("watchFace/watch_face_version.json").bufferedReader().use { it.readText() })
                val matches = (0 until resolutions.length()).map { resolutions.getJSONObject(it) }
                    .filter { it.optString("themeVersion") == description.screen }
                if (matches.size != 1 || matches.single().optString("screenResolution") != screen ||
                    compatible.none { HwtArchive.compatible(description.version, it) }) {
                    post { if (job === task) finish(task, R.string.wf_unsupported) }; return@execute
                }
                if (task.cancelled) return@execute
                val id = ids.reserve(existing)
                ownedIds.add(id)
                val state = LocalInstallState(id, "1.0.0")
                val payload = targets.call("decode", targets.call("payload"), app, task.payload.readBytes(),
                    screen == "466*466" && description.version.contains("2.0."), false) as? Map<*, *>
                require(payload?.get(HwtArchive.PAYLOAD) is ByteArray && (payload[HwtArchive.PAYLOAD] as ByteArray).isNotEmpty())
                if (task.cancelled) return@execute
                if (signatureRequired && targets.call("requestSignature", targets.call("signature"), id, state.version, 0, false) != true) {
                    post { if (job === task) finish(task, R.string.wf_signature) }; return@execute
                }
                post {
                    if (job !== task) {
                        targets.call("removeSignature", targets.call("signature"), id, state.version)
                        return@post
                    }
                    task.state = state
                    if (!connected() || device() != task.device) { finish(task, R.string.wf_disconnected); return@post }
                    targets.call("putCache", targets.call("cache"), task.taskId, payload)
                    targets.call("setId", manager, id)
                    targets.call("setVersion", manager, state.version)
                    apply(task)
                }
            } catch (error: Exception) {
                diagnostic("prepare", error)
                post { if (job === task) finish(task, R.string.wf_invalid) }
            }
        }
    }

    private fun apply(task: Job) {
        val state = checkNotNull(task.state)
        val expected = state.phase
        val info = targets.infoConstructor.newInstance()
        targets.call("infoSetId", info, state.id)
        targets.call("infoSetVersion", info, state.version)
        targets.call("infoSetScreen", info, task.screen)
        show(task.page, R.string.wf_applying)
        task.deadline = SystemClock.elapsedRealtime() + 60_000
        task.commandIssued = true
        val callback = proxy(targets.callbackClass) { _, args ->
            val code = args[0] as Int
            val response = args.getOrNull(1) as? String
            post {
                if (job !== task || state.terminal || state.phase != expected ||
                    (!response.isNullOrEmpty() && response != task.taskId)) return@post
                when (code) {
                    103 -> if (state.applied(expected)) {
                        show(task.page, R.string.wf_verifying)
                        task.deadline = SystemClock.elapsedRealtime() + 30_000
                        targets.call("refresh", bt)
                    }
                    105 -> if (state.readyToTransfer()) transfer(task)
                    101 -> Unit
                    else -> abort(task, code)
                }
            }
        }
        // 与原本地导入的 operateType=1、签名标志一致，不修改设备能力或签名结果。
        localOperation.set(true)
        try { targets.call("operate", bt, info, 1, callback, true, true) }
        finally { localOperation.remove() }
    }

    private fun transfer(task: Job) {
        targets.call("setState", manager, 3)
        show(task.page, R.string.wf_progress, 0)
        val receiver: (String, Array<out Any?>) -> Unit = { name, args ->
            val code = args[0] as Int
            post {
                if (job !== task || task.state?.terminal != false) return@post
                when (name) {
                    "onFileTransferState" -> if (code in 0..100 && task.state?.phase == LocalInstallState.Phase.TRANSFERRING) {
                        task.deadline = SystemClock.elapsedRealtime() + 60_000
                        show(task.page, R.string.wf_progress, code)
                    }
                    "onFileRespond" -> if (code == 0) abort(task, code)
                    "onUpgradeFailed" -> abort(task, code)
                }
            }
        }
        val fileCallback = proxy(targets.fileCallbackClass, receiver)
        val appCallback = proxy(targets.appCallbackClass, receiver)
        worker.execute {
            try {
                if (task.cancelled) return@execute
                targets.call("transfer", config, task.payload.absolutePath, task.taskId, 1, fileCallback, appCallback)
            } catch (error: Exception) { diagnostic("transfer", error); post { if (job === task) abort(task, -2) } }
        }
    }

    /** 只接管本会话的传输完成；其余宿主回调原样放行。 */
    fun btResponse(code: Int, info: Any?): Boolean {
        if (code != 107) return false
        // 已核验的 handleReportStatus 始终传入 WatchResourcesInfo；不以全局当前 ID 猜测无身份回调。
        if (info == null || !targets.infoClass.isInstance(info)) return blockOtherApply()
        val id = targets.call("infoId", info) as? String
        val version = targets.call("infoVersion", info) as? String
        if ("${id}_${version}" in finishedTasks) return true
        val current = job ?: return false
        if (id != current.state?.id || version != current.state?.version) return false
        post { if (job === current && current.state?.transferred() == true) apply(current) }
        return true
    }

    fun blockOtherApply(): Boolean = uncertainTransfer || job?.lockedHost == true

    fun blockOtherOperation(): Boolean = blockOtherApply() && localOperation.get() != true

    fun notifyBusy() { main.post { Toast.makeText(app, text(R.string.wf_busy), Toast.LENGTH_SHORT).show() } }

    fun fillNames(value: Any?) {
        (value as? Map<*, *>)?.forEach { (key, info) ->
            if (key is String && key in ownedIds && info != null && (targets.call("infoName", info) as? String).isNullOrBlank()) {
                targets.call("infoSetName", info, text(R.string.wf_local_name, key))
            }
        }
    }

    private fun tick(task: Job) {
        main.postDelayed({ guarded {
            if (job !== task) return@guarded
            if (task.lockedHost) {
                if (!connected() || device() != task.device) { abort(task, -1); return@guarded }
                targets.call("setState", manager, if (task.state?.phase == LocalInstallState.Phase.TRANSFERRING) 3 else 2)
            }
            if (SystemClock.elapsedRealtime() > task.deadline ||
                (task.commandIssued && SystemClock.elapsedRealtime() - task.started > 15 * 60_000)) {
                abort(task, -3)
            } else tick(task)
        } }, 2_000)
    }

    private fun cancel() { job?.let { if (it.commandIssued) abort(it, -4) else finish(it, R.string.wf_cancelled) } }

    private fun abort(task: Job, code: Int) {
        if (job !== task) return
        task.state?.fail()
        if (task.commandIssued) {
            quarantined = true
            uncertainTransfer = true
            task.cancelled = true
            worker.execute {
                try { targets.call("stop", config, task.taskId, 1, proxy(targets.callbackClass) { _, _ -> }) }
                catch (error: Exception) { diagnostic("stop", error) }
            }
        }
        val message = when (code) {
            140009 -> R.string.wf_no_space
            140004 -> R.string.wf_face_limit
            else -> R.string.wf_failure
        }
        finish(task, message, code, retainPayload = task.commandIssued)
    }

    private fun finish(task: Job, @StringRes message: Int, vararg args: Any, retainPayload: Boolean = false) {
        if (job !== task) return
        task.cancelled = true
        task.state?.let { finishedTasks.add(task.taskId) }
        job = null
        task.page.dialog.get()?.dismiss()
        try {
            if (task.lockedHost) targets.call("setState", manager, 0)
            task.state?.let { state ->
                if (targets.call("currentId", manager) == state.id) {
                    targets.call("setId", manager, null)
                    targets.call("setVersion", manager, null)
                }
                targets.call("removeSignature", targets.call("signature"), state.id, state.version)
                if (!retainPayload) targets.call("removeCache", targets.call("cache"), task.taskId)
            }
        } catch (error: Exception) { diagnostic("release", error); quarantined = true }
        show(task.page, message, *args)
        updateButtons()
        if (!retainPayload) worker.execute {
            try { HwtArchive.clean(root, task.directory) } catch (error: Exception) { diagnostic("cleanup", error) }
        }
    }

    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { close() }; return }
        if (closed) return
        closed = true
        cancel()
        pages.keys.toList().forEach(::detach)
        pending.clear()
        ready = false
        worker.shutdown()
    }

    private fun connected() = targets.call("connected", manager) == true
    // 只在内存中比较当前穿戴设备，不使用账号中的手机 deviceId，不写入日志或磁盘。
    private fun device(): String {
        val info = targets.call("device", targets.call("api", null, app)) as? Map<*, *> ?: return ""
        return ((info["device_Identify"] as? String)?.takeIf { it.isNotBlank() }
            ?: info["deviceIdCutStr"] as? String).orEmpty()
    }
    private fun entries(): Map<String, String> = (targets.call("list", bt) as Map<*, *>).entries
        .mapNotNull { (key, info) -> if (key is String && info != null) key to (targets.call("infoVersion", info) as? String).orEmpty() else null }.toMap()
    private fun text(@StringRes id: Int, vararg args: Any) = resources.getString(id, *args)
    private fun show(page: Page, @StringRes id: Int, vararg args: Any) { page.status.get()?.text = text(id, *args) }
    private fun updateButtons() {
        pages.values.forEach { page ->
            page.action.get()?.apply {
                text = text(if (job?.page === page) R.string.wf_cancel else R.string.wf_import)
                isEnabled = !closed && ready && !quarantined && (job?.page === page || (job == null && pending.get() == null))
            }
        }
    }
    private fun proxy(type: Class<*>, action: (String, Array<out Any?>) -> Unit): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, args ->
            when (method.name) {
                "toString" -> "LocalWatchFaceCallback"
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args?.firstOrNull()
                else -> { action(method.name, args ?: emptyArray()); null }
            }
        }
    fun guarded(action: () -> Unit) {
        try { action() } catch (error: Exception) {
            diagnostic("callback", error)
            val failed = job
            main.post {
                if (job === failed) failed?.let { abort(it, -5) }
                updateButtons()
            }
        }
    }
    private fun post(action: () -> Unit) { main.post { if (!closed) guarded(action) } }
    private fun diagnostic(stage: String, error: Exception) { logger.warn("Local watch face $stage: ${error.javaClass.simpleName}") }
    companion object { const val REQUEST = 0x5846 }
}
