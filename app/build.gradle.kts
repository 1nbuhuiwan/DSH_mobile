plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dsh.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dsh.mobile"
        minSdk = 24
        targetSdk = 36          // Android 16
        versionCode = 1
        versionName = "1.0.0"   // 语义化版本；后续改进请向后延续（见 README「版本规范」）

        // 应用体积与内存最小化：只保留 arm64/arm + x86_64 的原生库，避免携带冗余 ABI。
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            // 轻量化：混淆 + 资源裁剪，减小 APK 体积。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // debug 包不裁剪，便于真机调试（签名由 Android Studio / AGP 默认自动生成）。
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    // 仅保留已配置 ABI 的原生库，显著压缩 APK。
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // 到极轻量的核心依赖：仅使用系统 WebView + CameraX + ZXing 核心解码。
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")

    // CameraX：二维码扫码相机预览
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // ZXing core：极小的纯 Java 二维码/条形码解码库（无模型下载、无 Play Services 依赖）
    implementation("com.google.zxing:core:3.5.3")
}
