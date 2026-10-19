import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

extensions.configure<ApplicationExtension> {
    namespace = "love.nairain.huawei"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "love.nairain.huawei"
        minSdk = 28
        targetSdk = 37
        versionCode = 15
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        // 保持现有工具链和字节码目标，不随 UI 迁移升级构建依赖。
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    lint {
        // 构建插件升级需要单独验证，不作为源码质量告警处理。
        disable += "AndroidGradlePluginVersion"
        // 固定依赖按独立升级任务验证，远端发布新版本不应使既有构建失败。
        disable += "GradleDependency"
        warningsAsErrors = true
    }

}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.dexkit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.savedstate)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    // 显式使用 Miuix 已有的返回事件依赖，适配宿主内普通 Dialog；不引入路由框架。
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.navigationevent.compose)
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
    testImplementation(libs.libxposed.api)
    testImplementation(libs.json.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.compose.ui.test)
}

// 可选的真实 APK 定位测试：桌面 native 库由验证环境提供，不打包进模块。
tasks.withType<Test>().configureEach {
    listOf("scan.native", "scan.apk", "scan.resources", "scan.output", "scan.fixture", "scan.structure", "scan.version").forEach { key ->
        systemProperty(key, providers.gradleProperty(key).getOrElse(""))
    }
}
