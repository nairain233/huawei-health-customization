package love.nairain.huawei.hook.resolver

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import love.nairain.huawei.hook.HookInstallPolicy

/** 17.0.7.320 的真实 Dex 名称；与布局定位无关。完整契约缺一项就关闭本功能。 */
internal class LocalWatchFaceTargets(loader: ClassLoader) {
    data class Spec(val key: String, val owner: String, val name: String, val result: String,
        val args: List<String> = emptyList(), val static: Boolean = false)
    data class FieldSpec(val key: String, val name: String, val type: String)

    val methods: Map<String, Method> = specs.associate { spec ->
        val owner = Class.forName(spec.owner, false, loader)
        val method = owner.getDeclaredMethod(spec.name, *spec.args.map { type(it, loader) }.toTypedArray())
        require(method.returnType == type(spec.result, loader) && Modifier.isStatic(method.modifiers) == spec.static)
        method.isAccessible = true
        spec.key to method
    }
    val infoClass: Class<*> = Class.forName(INFO, false, loader)
    val webClass: Class<*> = Class.forName(WEB, false, loader)
    val callbacks = callbackFields.associate { spec ->
        spec.key to Class.forName(MANAGER, false, loader).getDeclaredField(spec.name).apply {
            require(type == type(spec.type, loader) && !Modifier.isStatic(modifiers))
            isAccessible = true
        }
    }
    val operateCallbacks = Class.forName(BT, false, loader).getDeclaredField("mOperateCallbacks").apply {
        require(type == java.util.LinkedHashMap::class.java && !Modifier.isStatic(modifiers))
        isAccessible = true
    }

    fun method(key: String): Method = methods.getValue(key)
    fun call(key: String, receiver: Any? = null, vararg args: Any?): Any? = method(key).invoke(receiver, *args)
    fun callback(key: String, manager: Any): Any? = callbacks.getValue(key).get(manager)

    companion object {
        private const val BASE = "com.huawei.watchface."
        const val WEB = BASE + "api.WebViewActivity"
        const val MANAGER = BASE + "api.HwWatchFaceManager"
        const val BT = BASE + "api.HwWatchFaceBtManager"
        const val INFO = BASE + "mvp.model.datatype.WatchResourcesInfo"
        const val SUPPORT = BASE + "mvp.model.datatype.WatchFaceSupportInfo"
        const val CALLBACK = BASE + "utils.callback.IBaseResponseCallback"
        const val FILE_CALLBACK = BASE + "utils.callback.IFileTransferStateCallback"
        const val APP_CALLBACK = BASE + "utils.callback.IAppTransferFileResultAIDLCallback"
        private const val CONFIG = BASE + "manager.HwDeviceConfigManager"
        private const val STRING = "java.lang.String"
        private const val CONTEXT = "android.content.Context"
        private const val WEB_CLIENT = BASE + "mvp.ui.view.CustomWebView\$i0"
        private const val DESIGNER = BASE + "e2"

        fun accepts(version: String?, code: Long, enabled: Boolean): Boolean =
            enabled && HookInstallPolicy.acceptsVersion(version, code)

        val specs = listOf(
            Spec("nativeDesigner", DESIGNER, "b", DESIGNER, listOf(CONTEXT), true),
            Spec("nativePayload", DESIGNER, "a", "void", listOf("android.app.Activity", "java.lang.StringBuilder", "java.lang.StringBuffer", STRING, STRING)),
            Spec("nativeSignatureStep", DESIGNER, "a", "void", listOf("java.lang.String[]", "java.lang.StringBuffer", "java.lang.StringBuilder")),
            Spec("nativeContinue", DESIGNER, "a", "void", listOf(STRING, STRING, "java.lang.StringBuffer", "java.lang.StringBuilder")),
            Spec("randomVersion", MANAGER, "getRandomVersion", STRING),
            Spec("managerTransfer", MANAGER, "transferFile", "void", listOf(STRING, STRING, "int")),
            Spec("cancel", MANAGER, "cancelInstallWatchFace", "void", listOf(STRING, STRING)),
            Spec("installResponse", "$MANAGER\$4", "onResponse", "void", listOf("int", "java.lang.Object")),
            Spec("fileProgressHandler", MANAGER, "handleOnFileTransferState", "void", listOf("int")),
            Spec("fileResultHandler", MANAGER, "handleOnFileRespond", "void", listOf("int")),
            Spec("fileFailureHandler", MANAGER, "handleOnUpgradeFailed", "void", listOf("int", STRING)),
            Spec("stopResponse", "$MANAGER\$14", "onResponse", "void", listOf("int", "java.lang.Object")),
            Spec("page", WEB, "initView", "void"),
            Spec("result", WEB, "onActivityResult", "void", listOf("int", "int", "android.content.Intent")),
            Spec("destroy", WEB, "onDestroy", "void"),
            Spec("pageLoaded", WEB_CLIENT, "onPageFinished", "void", listOf("android.webkit.WebView", STRING)),
            Spec("navigate", WEB_CLIENT, "shouldOverrideUrlLoading", "boolean", listOf("android.webkit.WebView", STRING)),
            Spec("manager", MANAGER, "getInstance", MANAGER, listOf(CONTEXT), true),
            Spec("api", BASE + "api.HwWatchFaceApi", "getInstance", BASE + "api.HwWatchFaceApi", listOf(CONTEXT), true),
            Spec("device", BASE + "api.HwWatchFaceApi", "getDeviceInfo", "java.util.Map"),
            Spec("bt", BT, "getInstance", BT, listOf(CONTEXT), true),
            Spec("config", CONFIG, "getInstance", CONFIG, listOf(CONTEXT), true),
            Spec("connected", MANAGER, "isBtConnect", "boolean"),
            Spec("state", MANAGER, "getWatchFaceInstallState", "int"),
            Spec("setState", MANAGER, "changeInstallState", "void", listOf("int")),
            Spec("setId", MANAGER, "setCurrentInstallWatchFaceHiTopId", "void", listOf(STRING)),
            Spec("setVersion", MANAGER, "setCurrentInstallWatchFaceVersion", "void", listOf(STRING)),
            Spec("currentId", MANAGER, "getCurrentInstallWatchFaceHiTopId", STRING),
            Spec("currentVersion", MANAGER, "getCurrentInstallWatchFaceVersion", STRING),
            Spec("blockApply", MANAGER, "applyWatchFace", "void", listOf(STRING, STRING, "int", STRING, "boolean", "int", "boolean")),
            Spec("btResponse", "$MANAGER\$1", "onResponse", "void", listOf("int", "java.lang.Object")),
            Spec("listed", BT, "reportSuccessFaceInfo", "void"),
            Spec("names", MANAGER, "dealWatchFaceInfoTransmit", "void", listOf("java.util.HashMap")),
            Spec("list", BT, "getAllWatchInfoHash", "java.util.HashMap"),
            Spec("refresh", BT, "getDeviceWatchInfo", "void"),
            Spec("support", BT, "getWatchFaceSupportInfo", SUPPORT),
            Spec("signatureSupported", BT, "isSupportWatchfaceSignature", "boolean"),
            Spec("operate", BT, "operateDevice", "void", listOf(INFO, "int", CALLBACK, "boolean", "boolean")),
            Spec("report", BT, "reportForUi", "void", listOf("boolean", "int", STRING)),
            Spec("callbackLock", BT, "getCommandCallbackCallbackList", "java.lang.Object", static = true),
            Spec("screen", SUPPORT, "getWatchFaceScreen", STRING),
            Spec("maxVersion", SUPPORT, "getWatchFaceMaxVersion", STRING),
            Spec("compatible", SUPPORT, "getCompatibleList", "java.util.List"),
            Spec("compatibleVersions", BASE + "mvp.model.datatype.ScreenInfo", "getSupportVersion", STRING),
            Spec("infoId", INFO, "getWatchInfoId", STRING),
            Spec("infoVersion", INFO, "getWatchInfoVersion", STRING),
            Spec("infoName", INFO, "getWatchInfoName", STRING),
            Spec("infoSetName", INFO, "setWatchInfoName", "void", listOf(STRING)),
            Spec("cache", BASE + "s0", "a", BASE + "s0", static = true),
            Spec("putCache", BASE + "s0", "a", "void", listOf(STRING, "java.util.Map")),
            Spec("removeCache", BASE + "s0", "d", "java.util.Map", listOf(STRING)),
            Spec("signature", BASE + "k2", "a", BASE + "k2", static = true),
            Spec("requestSignature", BASE + "k2", "a", "boolean", listOf(STRING, STRING, "int", "boolean")),
            Spec("readSignature", BASE + "k2", "a", STRING, listOf(STRING, STRING)),
            Spec("removeSignature", BASE + "k2", "b", "void", listOf(STRING, STRING)),
            Spec("transfer", CONFIG, "a", "void", listOf(STRING, STRING, "int", FILE_CALLBACK, APP_CALLBACK)),
        )

        val callbackFields = listOf(
            FieldSpec("install", "mInstallWatchFaceCallback", CALLBACK),
            FieldSpec("file", "mFileTransferStateCallback", FILE_CALLBACK),
            FieldSpec("app", "mAppTransferFileResultAIDLCallback", APP_CALLBACK),
            FieldSpec("bt", "mBtResponseCallback", CALLBACK),
        )

        private fun type(name: String, loader: ClassLoader): Class<*> = when (name) {
            "void" -> Void.TYPE
            "int" -> Integer.TYPE
            "boolean" -> java.lang.Boolean.TYPE
            "byte[]" -> ByteArray::class.java
            "java.lang.String[]" -> Array<String>::class.java
            else -> Class.forName(name, false, loader)
        }
    }
}
