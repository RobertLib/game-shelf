// AGP 9 compiles Kotlin itself (built-in Kotlin); the Compose and serialization compiler plugins
// pin the Kotlin Gradle plugin version used for the whole build.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
