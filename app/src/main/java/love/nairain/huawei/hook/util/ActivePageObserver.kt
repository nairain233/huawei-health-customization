package love.nairain.huawei.hook.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/** attach 后即记录前台 Activity，供后台扫描完成时处理已经创建的页面。 */
object ActivePageObserver : Application.ActivityLifecycleCallbacks {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(Activity) -> Unit>()
    private var active = WeakReference<Activity>(null)
    private var registered = false

    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(this)
    }

    fun observe(listener: (Activity) -> Unit) {
        main.post {
            listeners += listener
            active.get()?.let(listener)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        active = WeakReference(activity)
        listeners.forEach { it(activity) }
    }

    override fun onActivityPaused(activity: Activity) {
        if (active.get() === activity) active = WeakReference(null)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
