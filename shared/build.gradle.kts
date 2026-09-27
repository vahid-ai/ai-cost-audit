plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    jvm("desktop")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation(compose.components.resources)
            implementation("io.ktor:ktor-client-core:3.5.0")
            implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("com.russhwolf:multiplatform-settings:1.3.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("com.russhwolf:multiplatform-settings-test:1.3.0")
            implementation("io.ktor:ktor-client-mock:3.5.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.5.0")
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation("io.ktor:ktor-client-cio:3.5.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.5.0")
        }
    }
}

android {
    namespace = "com.aispend.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
}
