import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
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
        versionCode = 12
        versionName = "1.2"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(libs.dexkit)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    testImplementation(libs.junit)
    testImplementation(libs.libxposed.api)
    testImplementation(libs.json.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// 可选的真实 APK 定位测试：桌面 native 库由验证环境提供，不打包进模块。
tasks.withType<Test>().configureEach {
    listOf("scan.native", "scan.apk", "scan.resources", "scan.output", "scan.fixture").forEach { key ->
        systemProperty(key, providers.gradleProperty(key).getOrElse(""))
    }
}
