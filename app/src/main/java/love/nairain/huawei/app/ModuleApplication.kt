package love.nairain.huawei.app

import android.app.Application
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 模块应用：负责与 LSPosed 服务建立连接并通知设置界面。
 */
class ModuleApplication : Application(), XposedServiceHelper.OnServiceListener {
    private val listeners = CopyOnWriteArraySet<ServiceStateListener>()

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(boundService: XposedService) {
        service = boundService
        notifyListeners()
    }

    override fun onServiceDied(deadService: XposedService) {
        if (service === deadService) {
            service = null
            notifyListeners()
        }
    }

    fun addServiceStateListener(listener: ServiceStateListener) {
        listeners.add(listener)
        listener.onServiceStateChanged(service)
    }

    fun removeServiceStateListener(listener: ServiceStateListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            try {
                listener.onServiceStateChanged(service)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Service state listener failed", error)
            }
        }
    }

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    companion object {
        private const val TAG = "HuaweiTrim"
        @Volatile
        var service: XposedService? = null
            private set
    }
}
