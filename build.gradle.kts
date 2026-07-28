plugins {
    // Declare plugins without applying them — subprojects apply as needed.
    kotlin("multiplatform").apply(false)
    id("org.jetbrains.compose").apply(false)
    id("org.jetbrains.kotlin.plugin.compose").apply(false)
    id("com.android.application").apply(false)
    id("com.android.kotlin.multiplatform.library").apply(false)
}
