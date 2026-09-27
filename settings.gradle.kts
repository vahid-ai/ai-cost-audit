rootProject.name = "ai-spend"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

fun androidSdkAvailable(): Boolean {
    if (providers.environmentVariable("ANDROID_HOME").isPresent ||
        providers.environmentVariable("ANDROID_SDK_ROOT").isPresent) return true
    val localProps = File(rootDir, "local.properties")
    if (localProps.exists() && localProps.readText().contains("sdk.dir=")) return true
    return false
}

include(":shared")
include(":desktopApp")
if (androidSdkAvailable()) {
    include(":androidApp")
} else {
    println("Android SDK not detected; skipping :androidApp (see README).")
}
