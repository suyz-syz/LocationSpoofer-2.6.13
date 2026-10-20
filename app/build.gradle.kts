@file:Suppress("DEPRECATION")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "com.suseoaa.locationspoofer"
    compileSdk = 37

    fun getLocalConfig(key: String): String? {
        val localYml = file("../local.yml")
        if (localYml.exists()) {
            val line = localYml.readLines().find { it.startsWith("$key:") }
            if (line != null) {
                return line.substringAfter(":").trim().removeSurrounding("\"")
                    .removeSurrounding("'")
            }
        }
        return null
    }

    val googleMapsApiKey =
        System.getenv("GOOGLE_MAPS_API_KEY") ?: getLocalConfig("GOOGLE_MAPS_API_KEY") ?: ""

    defaultConfig {
        applicationId = "com.suseoaa.locationspoofer"
        minSdk = 26
        targetSdk = 37
        versionCode = 20613
        versionName = "2.6.13"

        vectorDrawables {
            useSupportLibrary = true
        }

        manifestPlaceholders["googleMapsApiKey"] = googleMapsApiKey

        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a")
                isUniversalApk = false
            }
        }
    }
    // 签名：优先读 CI 环境变量（release.yml / build-tag.yml 传入），其次是本地 keystore.properties；
    // 两者都没有时（云端无密钥）安全回退 debug 签名，保证 assembleRelease 不崩、APK 可安装。
    // 硬编码的 /Users/vincent/... 已删除，不再依赖作者本机文件。
    fun signingValue(env: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() }

    val keystorePath = signingValue("KEYSTORE_FILE_PATH")
    val hasReleaseKeystore = keystorePath != null && file(keystorePath).exists()

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(keystorePath!!)
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            }
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            // 没配置发布密钥时回退到 debug 签名，保证新克隆仓库的人也能直接编译调试
            signingConfig = signingConfigs.getByName(if (hasReleaseKeystore) "release" else "debug")
            buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 无发布密钥时回退到 debug 签名（而非不签名），保证云端产出的 APK 可安装；
            // 有发布密钥（CI secrets 注入 KEYSTORE_FILE_PATH 等环境变量）才用 release 签名。
            // 不再触发 ValidateSigningTask，也避免了"安装包没有签名文件"。
            signingConfig = signingConfigs.getByName(if (hasReleaseKeystore) "release" else "debug")
            buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xskip-metadata-version-check",
            "-opt-in=kotlinx.serialization.InternalSerializationApi"
        )
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    implementation(libs.xposed.service)
    implementation(libs.koin.androidx.compose)
    implementation(libs.amap.map)
    implementation(libs.amap.search)
    implementation(libs.baidu.map)
    implementation(libs.baidu.location)
    implementation(libs.baidu.search)
    implementation(libs.google.maps)
    implementation(libs.google.places)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Room (KSP)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    // 真实的 org.json 实现：Android 平台 jar 里的 org.json 是桩实现，
    // 方法体直接抛 "not mocked"，测试用的类路径顺序会让这个真实依赖优先生效。
    testImplementation(libs.org.json)
}
