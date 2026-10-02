package love.nairain.huawei.hook.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import love.nairain.huawei.hook.resolver.ActiveFragmentTargets
import java.lang.ref.WeakReference

/** attach 后即记录前台 Activity，供后台扫描完成时处理已经创建的页面。 */
object ActivePageObserver : Application.ActivityLifecycleCallbacks {
    private val main = Handler(Looper.getMainLooper())
    private class Listener(val scope: LayoutCallbackScope, val apply: (Activity) -> Unit) {
        var unregister: () -> Unit = {}
    }
    private val listeners = mutableListOf<Listener>()
    private var active = WeakReference<Activity>(null)
    private var registered = false

    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(this)
    }

    fun observe(scope: LayoutCallbackScope, apply: (Activity) -> Unit) {
        scope.afterActivation {
            main.post {
                scope.run {
                    val listener = Listener(scope, apply)
                    listeners += listener
                    listener.unregister = scope.onClose {
                        dispatch { listeners.remove(listener); listener.unregister() }
                    }
                    active.get()?.takeUnless { it.isDestroyed || it.isFinishing }?.let(apply)
                }
            }
        }
    }

    internal fun observeFragment(scope: LayoutCallbackScope, type: Class<*>, apply: (View) -> Unit) {
        observe(scope) { activity ->
            ActiveFragmentTargets.forEach(activity, type) { fragment ->
                val view = fragment.javaClass.getMethod("getView").invoke(fragment) as? View
                if (view != null && view.isAttachedToWindow) scope.applyPage(view, apply)
            }
        }
    }

    private fun dispatch(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.isDestroyed || activity.isFinishing) return
        active = WeakReference(activity)
        listeners.toList().forEach { listener -> listener.scope.run { listener.apply(activity) } }
    }

    override fun onActivityPaused(activity: Activity) {
        if (active.get() === activity) active = WeakReference(null)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (active.get() === activity) active = WeakReference(null)
    }
}
