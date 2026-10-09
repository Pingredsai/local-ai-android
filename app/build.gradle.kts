plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ============================================================
//  native 层开关
//
//  false（默认）：不编译 C++/JNI，纯 Kotlin 构建。
//    优点：不需要 NDK，环境没装好也能立刻跑通 UI（自动使用 MockEngine）。
//  true：编译 JNI，需要已安装 NDK。
//
//  修改位置：gradle.properties -> localai.enableNative
// ============================================================
val enableNative: Boolean =
    (project.findProperty("localai.enableNative") as String?)?.toBoolean() ?: false

android {
    namespace = "com.example.localai"
    compileSdk = 35

    if (enableNative) {
        ndkVersion = "30.0.16248370"
    }

    defaultConfig {
        applicationId = "com.example.localai"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        if (enableNative) {
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            externalNativeBuild {
                cmake {
                    cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                    arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DUSE_LLAMA_CPP=ON",
                    // ★ 关键性能开关：交叉编译时 ggml 默认不带 -march，
                    //   量化矩阵乘法会退化成慢速实现（实测慢 20-30 倍）。
                    //   armv8.2-a + dotprod + fp16 覆盖 2018 年后的主流中高端芯片。
                    //   若要兼容更老的设备，改成 "armv8-a"。
                    "-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16"
                )
                }
            }
        }
    }

    if (enableNative) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "4.1.2"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}