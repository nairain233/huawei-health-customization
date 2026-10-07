package love.nairain.huawei.watchface

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.webkit.WebView
import androidx.annotation.StringRes
import love.nairain.huawei.R
import love.nairain.huawei.hook.resolver.LocalWatchFaceTargets
import love.nairain.huawei.hook.util.ModuleLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** 页面反馈在主线程更新；原生跨线程许可由独立会话同步，页面只持有弱引用。 */
internal class LocalWatchFaceRuntime(
    private val app: Context,
    private val targets: LocalWatchFaceTargets,
    private val logger: ModuleLogger,
    private val serviceConflict: Boolean,
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "LocalWatchFaceIO").apply { isDaemon = true } }
    private val moduleContext = app.createPackageContext("love.nairain.huawei", 0)
    private val resources = moduleContext.resources
    private var footerScript = ""
    private val root = File(app.filesDir, "huawei_hook_local_faces")
    private val ids = LocalFaceIds(File(root, "ids"))
    private val pages = WeakHashMap<Activity, Page>()
    private val ownedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    // 仅保留小型身份许可，阻止已结束任务的迟到原生续接，不持有页面或文件内容。
    private val nativeSessions = java.util.concurrent.ConcurrentHashMap<String, NativeLocalInstallSession>()
    // 仅标记当前同步原生回调的来源，不用它授权跨线程安装操作。
    private val callbackDispatch = ThreadLocal<NativeLocalInstallSession>()
    private val payloadDispatch = ThreadLocal<NativeLocalInstallSession>()
    private var ready = false
    private var closed = false
    private var quarantined = false
    @Volatile private var uncertainTransfer = false
    private var pending = WeakReference<Activity>(null)
    @Volatile private var job: Job? = null
    private val manager get() = checkNotNull(targets.call("manager", null, app))
    private val bt get() = checkNotNull(targets.call("bt", null, app))
    private val config get() = checkNotNull(targets.call("config", null, app))

    private class Page(activity: Activity, web: WebView, var message: String) {
        val activity = WeakReference(activity)
        val web = WeakReference(web)
        val token = UUID.randomUUID().toString()
        var dialog = WeakReference<AlertDialog>(null)
        val feedback = LocalImportFeedback(activity)
    }

    private class Job(val page: Page, val directory: File, val device: String) {
        var description: HwtArchive.Description? = null
        @Volatile var state: LocalInstallState? = null
        @Volatile var waitingList = false
        @Volatile var lockedHost = false
        @Volatile var cancelled = false
        val commandIssued get() = native?.commandIssued == true
        @Volatile var native: NativeLocalInstallSession? = null
        var designer: Any? = null
        var hostManager: Any? = null
        var hostBt: Any? = null
        var hostConfig: Any? = null
        var resultShown = false
        var stopRequested = false
        @Volatile var payloadDigest: String? = null
        var recordedProgress = -1
        var stage = LocalImportFailure.Stage.READING
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
                ownedIds.addAll(ids.legacyIds())
                val script = moduleContext.assets.open("local-watchface-footer.js").bufferedReader().use { it.readText() }
                post { if (!closed) { footerScript = script; ready = true; updateButtons() } }
            } catch (error: Exception) {
                diagnostic("initialize", error)
                post { quarantined = true; pages.values.forEach { show(it, R.string.wf_restart) }; updateButtons() }
            }
        }
    }

    @android.annotation.SuppressLint("DiscouragedApi") // 资源属于已核验宿主，模块 R 无法引用；名称失配时跳过入口。
    fun attach(activity: Activity) {
        if (closed || pages.containsKey(activity)) return
        val webId = activity.resources.getIdentifier("web_view", "id", app.packageName)
        val web = activity.findViewById<WebView>(webId) ?: return
        val page = Page(activity, web, text(when {
            quarantined -> R.string.wf_restart
            serviceConflict -> R.string.wf_conflict
            else -> R.string.wf_ready
        }))
        pages[activity] = page
        updateButtons()
    }

    fun detach(activity: Activity) {
        val page = pages.remove(activity) ?: return
        if (pending.get() === activity) pending.clear()
        if (job?.page === page) cancel()
        page.dialog.get()?.dismiss()
        page.feedback.dismiss()
        page.web.get()?.evaluateJavascript("window.__huaweiHookLocalFace?.dispose()", null)
        updateButtons()
    }

    fun pageLoaded(web: WebView) { pages.values.firstOrNull { it.web.get() === web }?.let(::renderPage) }

    fun navigate(web: WebView, url: String?): Boolean {
        if (url?.startsWith("huawei-local-watchface:") != true) return false
        val page = pages.values.firstOrNull { it.web.get() === web } ?: return true
        if (LocalFacePagePolicy.acceptsPage(web.url) && LocalFacePagePolicy.acceptsAction(url, page.token) && web.hasWindowFocus()) {
            choose(page)
        }
        return true
    }

    private fun choose(page: Page) {
        val activity = page.activity.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        if (!ready || quarantined || job != null || pending.get() != null) return
        if (!connected()) { notice(page, R.string.wf_disconnected); return }
        if (targets.call("state", manager) != 0) { notice(page, R.string.wf_busy); return }
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
            pending.clear(); notice(page, R.string.wf_no_picker); updateButtons()
        } catch (error: SecurityException) {
            diagnostic("picker", error)
            pending.clear(); notice(page, R.string.wf_invalid); updateButtons()
        }
    }

    fun result(activity: Activity, request: Int, result: Int, intent: Intent?): Boolean {
        if (request != REQUEST || pending.get() !== activity) return false
        pending.clear()
        val page = pages[activity] ?: return true
        val uri = intent?.data
        if (result != Activity.RESULT_OK || uri == null) { show(page, R.string.wf_cancelled); updateButtons(); return true }
        if (uri.scheme != "content") { notice(page, R.string.wf_invalid); updateButtons(); return true }
        val device = device()
        if (!connected() || device.isEmpty()) { notice(page, R.string.wf_disconnected); updateButtons(); return true }
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
        task.stage = LocalImportFailure.Stage.CONFIRMING
        task.page.feedback.dismiss()
        val dialog = AlertDialog.Builder(LocalImportFeedback.themed(activity))
            .setTitle(text(R.string.wf_import)).setMessage(text(R.string.wf_confirm))
            .setPositiveButton(text(R.string.wf_install)) { _, _ -> post { if (job === task) begin(task) } }
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
        if (awaiting && love.nairain.huawei.BuildConfig.DEBUG) {
            event(if ("000000001" in snapshot) "native_designer_slot_present" else "native_designer_slot_empty")
        }
        post {
            if (job !== task) return@post
            if (!connected() || device() != task.device) { abort(task, -1); return@post }
            if (awaiting && task.waitingList) {
                task.waitingList = false
                prepare(task, snapshot[LocalInstallIdentity.ID])
            } else if (task.state?.verify(snapshot, observed) == true) finish(task, R.string.wf_success)
        }
    }

    private fun prepare(task: Job, currentVersion: String?) {
        val support = targets.call("support", bt) ?: return finish(task, R.string.wf_unsupported)
        val screen = targets.call("screen", support) as? String ?: return finish(task, R.string.wf_unsupported)
        val maximum = targets.call("maxVersion", support) as? String ?: ""
        val compatible = (targets.call("compatible", support) as? List<*>).orEmpty().filterNotNull()
            .flatMap { (targets.call("compatibleVersions", it) as? String).orEmpty().split(',') } + maximum
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
                val id = LocalInstallIdentity.ID
                ownedIds.add(id)
                val version = LocalInstallIdentity.version(currentVersion,
                    nativeSessions.values.filter { it.state.id == id }.map { it.state.version }.toSet()) {
                    targets.call("randomVersion", manager) as String
                }
                val state = LocalInstallState(id, version)
                post {
                    if (job !== task || task.cancelled) return@post
                    if (!connected() || device() != task.device) { finish(task, R.string.wf_disconnected); return@post }
                    task.state = state
                    task.hostManager = manager
                    task.hostBt = bt
                    task.hostConfig = config
                    task.designer = checkNotNull(targets.call("nativeDesigner", null, app))
                    val session = NativeLocalInstallSession(state, task.payload.absolutePath)
                    task.native = session
                    nativeSessions[session.taskId] = session
                    targets.call("setId", manager, id)
                    targets.call("setVersion", manager, state.version)
                    worker.execute {
                        try {
                            payloadDispatch.set(session)
                            if (!session.cancelled) targets.call("nativePayload", task.designer, null,
                                StringBuilder(session.taskId), StringBuffer(File(task.directory, "unpacked").absolutePath),
                                screen, description.version)
                        } catch (error: Exception) {
                            diagnostic("native_payload", error)
                            session.cancel()
                            post { if (job === task) abort(task, -2) }
                        } finally {
                            payloadDispatch.remove()
                            session.payloadReturned()
                            post { releaseIfFinished(task) }
                        }
                    }
                }
            } catch (error: Exception) {
                diagnostic("prepare", error)
                post { if (job === task) finish(task, R.string.wf_invalid) }
            }
        }
    }

    /** 只修改本任务许可；其余原生设计师续接原样执行。 */
    fun nativeStep(receiver: Any?, args: List<Any?>, signature: Boolean, proceed: () -> Any?): Any? {
        val identity = (args[if (signature) 2 else 3] as? StringBuilder)?.toString()
        val session = identity?.let(nativeSessions::get) ?: return proceed()
        val path = (args[if (signature) 1 else 2] as? StringBuffer)?.toString()
        val task = job
        if (path != session.payloadPath || task?.native !== session || receiver !== task.designer) {
            session.cancel()
            post { task?.let { if (it.native === session) abort(it, -2) } }
            return null
        }
        val parts = if (signature) (args[0] as? Array<*>)?.toList() else args.take(2)
        if (parts != listOf(session.state.id, session.state.version)) {
            session.cancel()
            post { if (job === task) abort(task, -2) }
            return null
        }
        val accepted = if (signature) session.beginSignature() else session.beginContinuation()
        if (!accepted) { post { releaseIfFinished(task) }; return null }
        try {
            return proceed()
        } finally {
            if (signature) session.signatureReturned() else session.continuationReturned()
            post { releaseIfFinished(task) }
        }
    }

    fun cachePayload(identity: String?, value: Any?): Boolean {
        val session = payloadDispatch.get()?.takeIf { it.taskId == identity } ?: return true
        val payload = (value as? Map<*, *>)?.get(HwtArchive.PAYLOAD)
        if (session.cache(payload)) {
            if (love.nairain.huawei.BuildConfig.DEBUG) {
                job?.takeIf { it.native === session }?.payloadDigest = LocalSignatureEvidence.digest(payload as ByteArray)
                event("native_payload_ready")
            }
            return true
        }
        val task = job?.takeIf { it.native === session }
        session.cancel()
        post { task?.let { if (job === it) abort(it, -2) } }
        return false
    }

    /** 签名管理器仍由原生调用；只读取业务码，失败时阻止随后排队的安装续接。 */
    fun signatureResponse(id: String?, version: String?) {
        val task = job ?: return
        val session = task.native ?: return
        if (!session.matches(id, version) || session.cancelled) return
        if (targets.call("signatureSupported", task.hostBt) != true) return
        val raw = targets.call("readSignature", targets.call("signature"), id, version) as? String
        val code = LocalImportFailure.signatureResult(raw)
        event("signature_response", code)
        if (love.nairain.huawei.BuildConfig.DEBUG) {
            val evidence = LocalSignatureEvidence.inspect(raw, session.state.id, session.state.version, task.payloadDigest)
            event("signature_identity_${evidence.identity}")
            event("signature_hash_${evidence.hash}")
            evidence.status?.let { event("signature_content_status", it) }
        }
        if (code == 0) return
        session.cancel()
        post {
            if (job === task) {
                if (code == null) finish(task, R.string.wf_signature)
                else finish(task, R.string.wf_signature_response, code)
            }
        }
    }

    fun blockOtherApply(receiver: Any?, args: List<Any?>): Boolean {
        val dispatch = callbackDispatch.get()
        if (dispatch?.cancelled == true && dispatch.matches(args[0] as? String, args[1] as? String)) return true
        val task = job
        if (task?.lockedHost != true) {
            if (!uncertainTransfer && dispatch == null && receiver === manager) {
                nativeSessions["${args[0]}_${args[1]}"]?.reuseByHost()
            }
            return uncertainTransfer
        }
        val session = task.native ?: return true
        if (receiver !== task.hostManager || !session.matches(args[0] as? String, args[1] as? String)) return true
        if (args[2] != 2 || args[3] != "" || args[4] != false || args[5] != 0) return true
        val allowed = session.apply(args[6] == true)
        if (allowed) post {
            if (job === task && !task.resultShown) {
                // 早于宿主 60 秒重置，确保取消仍能匹配当前身份并进入原生停止。
                task.deadline = SystemClock.elapsedRealtime() + 45_000
                show(task.page, R.string.wf_applying)
                event(if (args[6] == true) "native_apply_signed" else "native_apply_transferred")
            }
        }
        return !allowed
    }

    fun blockOtherOperation(receiver: Any?, args: List<Any?>): Boolean {
        val task = job
        if (task?.lockedHost != true) return uncertainTransfer
        val session = task.native ?: return true
        val info = args[0] ?: return true
        return receiver !== task.hostBt || !targets.infoClass.isInstance(info) ||
            !session.matches(targets.call("infoId", info) as? String, targets.call("infoVersion", info) as? String) ||
            args[1] != 1 || args[2] !== targets.callback("install", task.hostManager!!) || args[3] != true ||
            !session.permitsOperation(args[4] == true)
    }

    /** 观察真正序列化前的对象；false 参数之外，再核实签名字段确实为空。 */
    fun nativeCommand(receiver: Any?, args: List<Any?>) {
        if (!love.nairain.huawei.BuildConfig.DEBUG) return
        val task = job ?: return
        val session = task.native ?: return
        val info = args[0] ?: return
        if (receiver !== task.hostBt || args[1] != 1 || !targets.infoClass.isInstance(info) ||
            !session.matches(targets.call("infoId", info) as? String, targets.call("infoVersion", info) as? String)) return
        event(if ((targets.call("infoSignature", info) as? String).isNullOrEmpty()) "native_command_unsigned" else "native_command_signed")
    }

    fun apiTransfer(args: List<Any?>) {
        if (!love.nairain.huawei.BuildConfig.DEBUG) return
        val task = job ?: return
        if (args[1] == task.taskId && args[0] == task.native?.payloadPath &&
            args[3] === targets.callback("app", task.hostManager!!)) event("native_api_transfer")
    }

    /** 当前任务只使用本地载荷路径，设计师入口及重复 105 最多共同发起一次传输。 */
    fun nativeTransfer(receiver: Any?, args: List<Any?>, proceed: (List<Any?>) -> Any?): Any? {
        val session = (args[1] as? String)?.let(nativeSessions::get) ?: return proceed(args)
        val task = job
        when (session.transferRoute(task?.native === session, callbackDispatch.get() === session, args[0])) {
            NativeLocalInstallSession.TransferRoute.ORIGINAL -> return proceed(args)
            NativeLocalInstallSession.TransferRoute.SKIP -> return null
            NativeLocalInstallSession.TransferRoute.LOCAL -> Unit
        }
        if (task == null) return null
        if (receiver !== task.hostManager || args[2] != 1 || !session.scheduleTransfer()) return null
        post {
            if (job === task && !task.resultShown) {
                task.deadline = SystemClock.elapsedRealtime() + 45_000
                show(task.page, R.string.wf_progress, 0)
                event("native_transfer")
            }
        }
        return proceed(args.toMutableList().apply { this[0] = session.payloadPath })
    }

    fun nativeFileTransfer(receiver: Any?, args: List<Any?>, proceed: () -> Any?): Any? {
        val session = (args[1] as? String)?.let(nativeSessions::get) ?: return proceed()
        val task = job
        when (session.transferRoute(task?.native === session, false, args[0])) {
            NativeLocalInstallSession.TransferRoute.ORIGINAL -> return proceed()
            NativeLocalInstallSession.TransferRoute.SKIP -> {
                // 已排队但取消的本任务仍需出队，才能解除文件保留。
                if (task?.native === session) { session.beginTransfer(); post { releaseIfFinished(task) } }
                return null
            }
            NativeLocalInstallSession.TransferRoute.LOCAL -> Unit
        }
        if (task == null) return null
        if (receiver !== task.hostConfig || args[0] != session.payloadPath || args[2] != 1 ||
            args[3] !== targets.callback("file", task.hostManager!!) || args[4] !== targets.callback("app", task.hostManager!!)) return null
        val accepted = session.beginTransfer()
        if (!accepted) { post { releaseIfFinished(task) }; return null }
        event("native_file_dispatch")
        try { return proceed() } finally {
            session.transferReturned()
            event("native_file_dispatch_returned")
            post { releaseIfFinished(task) }
        }
    }

    /** 107 原回调继续执行；只提前记录许可，使其第二次 apply 能通过守卫。 */
    fun btResponse(receiver: Any?, code: Int, info: Any?) {
        val task = job ?: return
        val session = task.native ?: return
        if (receiver !== targets.callback("bt", task.hostManager!!) || code != 107 ||
            info == null || !targets.infoClass.isInstance(info)) return
        if (!session.matches(targets.call("infoId", info) as? String, targets.call("infoVersion", info) as? String)) return
        session.transferred()
        event("native_transfer_complete", code)
        post { releaseIfFinished(task) }
    }

    fun nativeBtResponse(receiver: Any?, code: Int, info: Any?, proceed: () -> Any?): Any? {
        val session = if (code == 107 && info != null && targets.infoClass.isInstance(info)) {
            nativeSessions["${targets.call("infoId", info)}_${targets.call("infoVersion", info)}"]?.takeUnless { it.hostReused }
        } else null
        // 宿主 107 分支仅比较 ID；旧版本仍会关闭新任务弹窗并尝试再次应用。
        if (session != null && job?.native !== session) return null
        val previous = callbackDispatch.get()
        if (session != null) callbackDispatch.set(session)
        session?.callbackStarted()
        try {
            guarded { btResponse(receiver, code, info) }
            return proceed()
        } finally {
            session?.callbackReturned()
            if (session != null) post { job?.takeIf { it.native === session }?.let(::releaseIfFinished) }
            if (previous == null) callbackDispatch.remove() else callbackDispatch.set(previous)
        }
    }

    fun installResponse(receiver: Any?, code: Int, identity: Any?) {
        val task = job ?: return
        val session = task.native ?: return
        if (receiver !== targets.callback("install", task.hostManager!!)) return
        val response = identity as? String
        if (response != session.taskId) return
        event("native_apply_response", code)
        when (code) {
            103 -> if (session.applied()) post {
                if (job === task && !task.resultShown) {
                    show(task.page, R.string.wf_verifying)
                    task.deadline = SystemClock.elapsedRealtime() + 30_000
                    targets.call("refresh", task.hostBt)
                }
            }
            101, 105 -> Unit
            else -> { session.cancel(); post { if (job === task) abort(task, code) } }
        }
    }

    fun nativeInstallResponse(receiver: Any?, code: Int, identity: Any?, proceed: () -> Any?): Any? {
        val session = (identity as? String)?.let(nativeSessions::get)?.takeUnless { it.hostReused }
        // 同一个原生槽位的旧版本回调不能重写新版本的安装身份。
        if (session != null && job?.native !== session) return null
        val previous = callbackDispatch.get()
        if (session != null) callbackDispatch.set(session)
        session?.callbackStarted()
        try {
            guarded { installResponse(receiver, code, identity) }
            return proceed()
        } finally {
            session?.callbackReturned()
            if (session != null) post { job?.takeIf { it.native === session }?.let(::releaseIfFinished) }
            if (previous == null) callbackDispatch.remove() else callbackDispatch.set(previous)
        }
    }

    fun fileEvent(receiver: Any?, kind: String, code: Int) {
        val task = job ?: return
        val session = task.native ?: return
        if (!session.observesFileCallbacks) return
        if (receiver !== task.hostManager || targets.call("currentId", receiver) != session.state.id ||
            targets.call("currentVersion", receiver) != session.state.version) return
        if (kind == "fileFailureHandler" || (kind == "fileResultHandler" && code == 0)) {
            event("native_file_failure", code)
            session.cancel()
            post { if (job === task) abort(task, code) }
        } else if (kind == "fileProgressHandler" && code in 0..100) post {
            if (job === task && !task.resultShown && task.state?.phase == LocalInstallState.Phase.TRANSFERRING) {
                if (task.recordedProgress < 0 || (task.recordedProgress == 0 && code > 0) || code == 100 && task.recordedProgress != 100) {
                    event("native_file_progress", code)
                    task.recordedProgress = code
                }
                task.deadline = SystemClock.elapsedRealtime() + 45_000
                show(task.page, R.string.wf_progress, code)
            }
        }
    }

    fun nativeStopDispatch(receiver: Any?, args: List<Any?>) {
        val task = job ?: return
        val session = task.native ?: return
        val callback = args[2] ?: return
        if (!task.stopRequested || !task.cancelled || receiver !== task.hostConfig || args[0] != session.taskId ||
            args[1] != 1 || !targets.method("stopResponse").declaringClass.isInstance(callback)) return
        if (session.bindStopCallback(callback)) event("native_stop_dispatch")
    }

    fun nativeStopResponse(receiver: Any?, code: Int, identity: Any?, proceed: () -> Any?): Any? {
        val session = (identity as? String)?.let(nativeSessions::get)?.takeIf { it.ownsStopCallback(receiver) }
            ?: job?.native?.takeIf { it.ownsStopCallback(receiver) } ?: return proceed()
        if (!session.acceptsStopResponse(receiver, identity as? String)) return null
        session.callbackStarted()
        try {
            val result = proceed()
            session.stopResponse(code, identity as? String)
            event("native_stop_response", code)
            return result
        } finally {
            session.callbackReturned()
            post { job?.takeIf { it.native === session }?.let(::releaseIfFinished) }
        }
    }

    fun missingIdentityError(code: Int, identity: String?) {
        val task = job ?: return
        val session = task.native ?: return
        if (!LocalImportFailure.missingIdentityError(task.state, task.lockedHost, task.cancelled,
                connected() && device() == task.device, code, identity)) return
        // 只观察宿主仍登记的原生回调，不移除、不消费 reportForUi。
        val owned = synchronized(checkNotNull(targets.call("callbackLock"))) {
            val callbacks = targets.operateCallbacks.get(task.hostBt) as Map<*, *>
            callbacks[session.taskId] === targets.callback("install", task.hostManager!!)
        }
        if (!owned) return
        session.cancel()
        post { if (job === task) { event("unidentified_error", code); abort(task, code) } }
    }

    fun fillNames(value: Any?) {
        (value as? Map<*, *>)?.forEach { (key, info) ->
            if (key is String && key in ownedIds && info != null && (targets.call("infoName", info) as? String).isNullOrBlank()) {
                targets.call("infoSetName", info, text(R.string.wf_local_name, key))
            }
        }
    }

    private fun tick(task: Job) {
        main.postDelayed({ guarded {
            if (job !== task || task.resultShown) return@guarded
            if (task.lockedHost) {
                if (!connected() || device() != task.device) { abort(task, -1); return@guarded }
            }
            if (LocalImportFailure.timeoutStage(task.stage, SystemClock.elapsedRealtime(), task.deadline,
                    task.started, task.commandIssued) != null) {
                abort(task, -3)
            } else tick(task)
        } }, 2_000)
    }

    private fun cancel() { job?.let { if (it.commandIssued) abort(it, -4) else finish(it, R.string.wf_cancelled) } }

    private fun abort(task: Job, code: Int) {
        if (job !== task || task.resultShown) return
        event("failed_${task.stage.name.lowercase()}", code)
        task.native?.cancel() ?: task.state?.fail()
        task.cancelled = true
        requestNativeCancel(task)
        val message = when {
            LocalImportFailure.signatureRejected(code) -> R.string.wf_device_signature
            code == -1 || code == 141001 -> R.string.wf_connection_lost
            code == -3 -> R.string.wf_timeout
            code == -4 -> R.string.wf_cancelled_pending
            code == 140009 -> R.string.wf_no_space
            code == 140004 -> R.string.wf_face_limit
            else -> R.string.wf_failure
        }
        if (code == -3) finish(task, message, stageText(task.stage)) else finish(task, message, code)
    }

    private fun finish(task: Job, @StringRes message: Int, vararg args: Any) {
        if (job !== task || task.resultShown) return
        task.resultShown = true
        task.cancelled = true
        task.native?.cancel()
        task.page.dialog.get()?.dismiss()
        show(task.page, message, *args)
        if (!closed && pages[task.page.activity.get()] === task.page) {
            task.page.feedback.result(text(R.string.wf_import), text(message, *args), text(R.string.wf_ok))
        } else task.page.feedback.dismiss()
        uncertainTransfer = task.native?.canRelease() == false
        releaseIfFinished(task)
        updateButtons()
    }

    private fun releaseIfFinished(task: Job) {
        if (job === task && task.resultShown) requestNativeCancel(task)
        if (job !== task || !task.resultShown || task.native?.canRelease() == false) return
        // 一旦原生发过命令，安装状态只由原生完成或停止回调恢复。
        if (task.lockedHost && !task.commandIssued && targets.call("state", manager) == 2 &&
            (task.state == null || targets.call("currentId", manager) == task.state?.id)) {
            targets.call("setState", manager, 0)
        }
        job = null
        uncertainTransfer = false
        worker.execute {
            try {
                task.state?.let {
                    targets.call("removeSignature", targets.call("signature"), it.id, it.version)
                    targets.call("removeCache", targets.call("cache"), task.taskId)
                }
                HwtArchive.clean(root, task.directory)
            } catch (error: Exception) {
                diagnostic("cleanup", error)
                post { quarantined = true; updateButtons() }
            }
        }
        updateButtons()
    }

    private fun requestNativeCancel(task: Job) {
        if (!task.cancelled || !task.commandIssued || task.stopRequested ||
            task.state?.phase == LocalInstallState.Phase.SUCCEEDED || task.native?.canRequestStop() != true) return
        task.stopRequested = true
        task.native?.requestStop()
        event("native_stop_requested")
        if (love.nairain.huawei.BuildConfig.DEBUG) {
            event(if (targets.call("currentId", task.hostManager) == task.state?.id) "native_cancel_identity_match" else "native_cancel_identity_mismatch")
        }
        try { targets.call("cancel", task.hostManager, task.state!!.id, task.state!!.version) }
        catch (error: Exception) { diagnostic("native_cancel", error) }
    }

    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { close() }; return }
        if (closed) return
        closed = true
        cancel()
        pages.keys.toList().forEach(::detach)
        pending.clear()
        ready = false
        // 原生停止和排队续接可能晚于页面销毁；保持守护执行器以完成安全清理。
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
    private fun show(page: Page, @StringRes id: Int, vararg args: Any) {
        page.message = text(id, *args)
        renderPage(page)
        val task = job?.takeIf { it.page === page } ?: return
        val stage = when (id) {
            R.string.wf_preparing -> LocalImportFailure.Stage.READING
            R.string.wf_wait -> LocalImportFailure.Stage.DEVICE_LIST
            R.string.wf_signing -> LocalImportFailure.Stage.SIGNATURE
            R.string.wf_applying -> LocalImportFailure.Stage.APPLYING
            R.string.wf_progress -> LocalImportFailure.Stage.TRANSFERRING
            R.string.wf_verifying -> LocalImportFailure.Stage.VERIFYING
            else -> return
        }
        if (task.stage != stage) event("stage_${stage.name.lowercase()}")
        task.stage = stage
        page.feedback.progress(text(R.string.wf_import), text(id, *args), text(R.string.wf_cancel),
            if (id == R.string.wf_progress) args.firstOrNull() as? Int else null) { guarded { if (job === task) cancel() } }
    }
    private fun stageText(stage: LocalImportFailure.Stage): String = text(when (stage) {
        LocalImportFailure.Stage.READING -> R.string.wf_preparing
        LocalImportFailure.Stage.CONFIRMING -> R.string.wf_confirm
        LocalImportFailure.Stage.DEVICE_LIST -> R.string.wf_wait
        LocalImportFailure.Stage.SIGNATURE -> R.string.wf_signing
        LocalImportFailure.Stage.APPLYING -> R.string.wf_applying
        LocalImportFailure.Stage.TRANSFERRING -> R.string.wf_transferring
        LocalImportFailure.Stage.VERIFYING -> R.string.wf_verifying
    })
    private fun event(stage: String, code: Int? = null) {
        val message = "Local watch face $stage" + (code?.let { ": $it" } ?: "")
        logger.info(message)
        if (love.nairain.huawei.BuildConfig.DEBUG) android.util.Log.i("HuaweiTrim", message)
    }
    private fun updateButtons() {
        pages.values.forEach(::renderPage)
    }
    private fun renderPage(page: Page) {
        val web = page.web.get() ?: return
        if (footerScript.isBlank() || !LocalFacePagePolicy.acceptsPage(web.url)) return
        val data = JSONObject().put("token", page.token).put("title", text(R.string.wf_import))
            .put("message", page.message).put("enabled", !closed && ready && !quarantined && job == null && pending.get() == null)
        web.evaluateJavascript("$footerScript($data)", null)
    }
    private fun notice(page: Page, @StringRes id: Int) {
        show(page, id)
        page.feedback.result(text(R.string.wf_import), text(id), text(R.string.wf_ok))
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
    private fun post(action: () -> Unit) { main.post { guarded(action) } }
    private fun diagnostic(stage: String, error: Exception) { logger.warn("Local watch face $stage: ${error.javaClass.simpleName}") }
    companion object { const val REQUEST = 0x5846 }
}
