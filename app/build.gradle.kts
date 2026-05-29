// ── app/build.gradle.kts ──────────────────────────────────────────────────────
// Plugin ORDER matters: kotlin.multiplatform must be first

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    jvm("desktop")

    // ── iOS targets ───────────────────────────────────────────────────────────
    val iosArm64         = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()
    val iosX64           = iosX64()

    listOf(iosArm64, iosSimulatorArm64, iosX64).forEach { target ->
        // Cinterop: bind the pure-C audio engine interface
        target.compilations["main"].cinterops.create("audioEngine") {
            defFile(project.file("cinterop/audio_engine.def"))
            includeDirs("src/main/cpp")
        }
        // Static framework embedded by the Xcode run-script phase
        target.binaries.framework {
            baseName  = "ComposeApp"
            isStatic  = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // ── Shared JVM source set (androidMain + desktopMain) ─────────────────
        // Contains JVM-specific actual implementations (nanoNow, nanosleep, thread)
        val jvmMain by creating {
            dependsOn(commonMain)
        }

        val androidMain by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.oboe)
                implementation(libs.androidx.media)
                implementation(libs.coil.compose)
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
            }
        }

        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.rules)
                implementation(libs.androidx.compose.ui.test.junit4)
            }
        }

        val desktopMain by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        // ── iOS source sets ───────────────────────────────────────────────────
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Coil 3 supports iOS natively — used by BackgroundLayerIos
                implementation(libs.coil.compose)
            }
        }

        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        getByName("iosX64Main").dependsOn(iosMain)
    }
}

android {
    namespace   = "com.jayc180.rhythmengine"
    compileSdk  = 36

    defaultConfig {
        applicationId = "com.jayc180.rhythmengine"
        minSdk        = 26
        targetSdk     = 36
        versionCode   = 1
        versionName   = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake { path = file("CMakeLists.txt") }
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

    // NOTE: compileOptions block removed — jvmTarget is set via kotlinOptions above
    // Keeping it causes a duplicate-configuration warning with AGP 9.x + KMP

    buildFeatures {
        compose = true
        prefab  = true
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}
