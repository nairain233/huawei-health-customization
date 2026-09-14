package love.nairain.huawei.app

/**
 * 本模块使用的主要开源项目。
 *
 * 许可证标识和项目地址沿用依赖产物公开信息；新增主要依赖时应同步更新。
 */
internal data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val license: String,
    val url: String,
)

internal val openSourceLibraries: List<OpenSourceLibrary> = listOf(
    OpenSourceLibrary(
        name = "DexKit",
        author = "LuckyPray",
        license = "Apache-2.0",
        url = "https://github.com/LuckyPray/DexKit/blob/2.2.0/LICENSE",
    ),
    OpenSourceLibrary(
        name = "Miuix",
        author = "compose-miuix-ui",
        license = "Apache-2.0",
        url = "https://github.com/compose-miuix-ui/miuix",
    ),
    OpenSourceLibrary(
        name = "Jetpack Compose",
        author = "The Android Open Source Project",
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/compose",
    ),
    OpenSourceLibrary(
        name = "AndroidX",
        author = "androidx",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),
    OpenSourceLibrary(
        name = "LibXposed API",
        author = "libxposed",
        license = "Apache-2.0",
        url = "https://github.com/libxposed/api",
    ),
    OpenSourceLibrary(
        name = "LibXposed Service",
        author = "libxposed",
        license = "Apache-2.0",
        url = "https://github.com/libxposed/service",
    ),
    OpenSourceLibrary(
        name = "Kotlin",
        author = "Kotlin Team",
        license = "Apache-2.0",
        url = "https://github.com/JetBrains/kotlin",
    ),
)
