package love.nairain.huawei.hook

class HookRegistry(features: List<HookFeature>) {
    private val features = features.toList()

    fun installAll(context: HookContext): Map<String, InstallResult> =
        HookRegistryRunner.run(
            features,
            HookFeature::id,
            onFailure = { id, error -> context.logger.warn("Hook failed: $id", error) },
        ) { feature ->
            feature.install(context)
        }.also { results ->
            results.forEach { (id, result) ->
                when (result) {
                    is InstallResult.Installed -> context.logger.info(
                        "Hook installed: $id, count=${result.hookCount}",
                    )
                    InstallResult.Disabled -> context.logger.info("Hook disabled: $id")
                    is InstallResult.Unsupported -> context.logger.warn(
                        "Hook unsupported: $id, reason=${result.reason}",
                    )
                    is InstallResult.Failed -> context.logger.warn(
                        "Hook failed: $id, reason=${result.reason}",
                    )
                }
            }
        }
}
