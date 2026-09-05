package love.nairain.huawei.hook.util

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object ViewSelectors {
    fun findByResourceName(root: View, packageName: String, resourceName: String): View? {
        val id = ResourceIdResolver.id(root.resources, packageName, resourceName)
        return if (id == 0) null else root.findViewById(id)
    }

    fun applyByResourceNames(
        root: View,
        packageName: String,
        resourceNames: Map<String, String>,
        config: Map<String, Boolean>,
    ) {
        resourceNames.forEach { (name, key) ->
            findByResourceName(root, packageName, name)?.let { view ->
                if (config[key] == true) ViewTrimmer.collapse(view) else ViewTrimmer.restore(view)
            }
        }
    }

    fun text(root: View?): String? {
        if (root == null) return null
        if (root is TextView && root.text?.isNotBlank() == true) return root.text.toString()
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                text(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    fun collapseContainersByText(
        root: View,
        textKeys: Map<String, String>,
        allowedContainerNames: Set<String>,
        config: Map<String, Boolean>,
    ) {
        walk(root) { view ->
            val label = (view as? TextView)?.text?.toString() ?: return@walk
            val key = textKeys[label] ?: textKeys.entries.firstOrNull { (title, _) ->
                label.startsWith("$title |")
            }?.value ?: return@walk
            if (config[key] != true) return@walk
            var candidate: View? = view.parent as? View
            repeat(8) {
                val current = candidate ?: return@repeat
                if (resourceEntryName(current) in allowedContainerNames) {
                    ViewTrimmer.collapse(current)
                    return@walk
                }
                candidate = current.parent as? View
            }
        }
    }

    fun resourceEntryName(view: View?): String? = runCatching {
        if (view == null || view.id == View.NO_ID) null else view.resources.getResourceEntryName(view.id)
    }.getOrNull()

    fun hasAncestorResourceName(view: View?, resourceName: String): Boolean {
        var current = view
        while (current != null) {
            if (resourceEntryName(current) == resourceName) return true
            current = current.parent as? View
        }
        return false
    }

    private fun walk(root: View, block: (View) -> Unit) {
        block(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) walk(root.getChildAt(index), block)
        }
    }
}
