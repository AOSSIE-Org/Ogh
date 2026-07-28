plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = providers.environmentVariable("APP_VERSION_NAME")
    .orElse("0.1.0")
    .get()
val appVersionCode = providers.environmentVariable("APP_VERSION_CODE")
    .orElse("1")
    .get()
    .toIntOrNull()
    ?.takeIf { it > 0 }
    ?: error("APP_VERSION_CODE must be a positive integer")

val releaseSigningValues = listOf(
    providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull,
    providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull,
    providers.environmentVariable("RELEASE_KEY_ALIAS").orNull,
    providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull,
)
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
check(releaseSigningValues.all { it.isNullOrBlank() } || hasReleaseSigning) {
    "Release signing configuration is incomplete"
}

android {
    namespace = "com.ogh.app"
    compileSdk = (findProperty("android.compileSdk") as String).toInt()

    defaultConfig {
        applicationId = "com.ogh.app"
        minSdk = (findProperty("android.minSdk") as String).toInt()
        targetSdk = (findProperty("android.targetSdk") as String).toInt()
        versionCode = appVersionCode
        versionName = appVersionName

        // CI supplies the canonical URL from its repository context. Local builds
        // derive it from origin, so forks and repository renames need no code edits.
        val repositoryUrlFromGit = runCatching {
            val process = ProcessBuilder("git", "remote", "get-url", "origin").start()
            val remote = process.inputStream.bufferedReader().readText().trim()
            check(process.waitFor() == 0 && remote.isNotEmpty())
            when {
                remote.startsWith("git@") -> {
                    val (host, path) = remote.removePrefix("git@").split(":", limit = 2)
                    "https://$host/$path"
                }
                else -> remote
            }.removeSuffix(".git").trimEnd('/')
        }.getOrDefault("")
        val repoUrl = System.getenv("APP_REPOSITORY_URL")
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf(String::isNotEmpty)
            ?: repositoryUrlFromGit

        buildConfigField("String", "REPO_URL", "\"$repoUrl\"")
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigningValues[0]!!)
                storePassword = releaseSigningValues[1]
                keyAlias = releaseSigningValues[2]
                keyPassword = releaseSigningValues[3]
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    // Android entry-point integration; shared UI uses Compose Multiplatform.
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
    // Lifecycle 2.11 requires AGP 9.2/compileSdk 37; this project remains on
    // the supported AGP 8 KMP path until that migration is handled separately.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // RootEncoder — RTMP streaming with screen capture support
    implementation("com.github.pedroSG94.RootEncoder:library:2.7.5")

    // OAuth — AppAuth (no Play Services dependency, uses Custom Tabs)
    implementation("net.openid:appauth:0.11.1")

    // HTTP client for platform API calls
    implementation("com.squareup.okhttp3:okhttp:5.4.0")

    // AndroidX core
    // 1.19 requires compileSdk 37/AGP 9.1; 1.18 is the newest API 36 line.
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Unit tests
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
    testImplementation("org.json:json:20260522")
}
