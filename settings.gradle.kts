rootProject.name = "Ogh"

include(":androidApp")
include(":shared")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    plugins {
        val kotlinVersion = extra["kotlin.version"] as String
        val agpVersion = extra["agp.version"] as String
        val composeMultiplatformVersion = extra["compose.multiplatform.version"] as String

        kotlin("jvm").version(kotlinVersion)
        kotlin("multiplatform").version(kotlinVersion)
        kotlin("android").version(kotlinVersion)
        id("org.jetbrains.kotlin.plugin.compose").version(kotlinVersion)
        id("org.jetbrains.compose").version(composeMultiplatformVersion)

        id("com.android.application").version(agpVersion)
        id("com.android.kotlin.multiplatform.library").version(agpVersion)
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}
