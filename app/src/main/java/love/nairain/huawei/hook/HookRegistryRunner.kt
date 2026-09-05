package love.nairain.huawei.hook

/** 将逐项异常隔离做成可在 JVM 直接验证的纯编排器。 */
object HookRegistryRunner {
    fun <T> run(
        items: List<T>,
        idOf: (T) -> String,
        onFailure: (String, Throwable) -> Unit = { _, _ -> },
        install: (T) -> InstallResult,
    ): Map<String, InstallResult> = buildMap {
        items.forEach { item ->
            val result = try {
                install(item)
            } catch (error: Throwable) {
                onFailure(idOf(item), error)
                InstallResult.Failed(error.javaClass.simpleName)
            }
            put(idOf(item), result)
        }
    }
}
