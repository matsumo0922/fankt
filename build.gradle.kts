plugins {
    // Android
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false

    // Kotlin Multiplatform (KMP)
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.kmpCompose) apply false
    alias(libs.plugins.kmpComplete) apply false
    alias(libs.plugins.kmpSwiftKlib) apply false

    // Kotlin
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.android) apply false

    // Publishing
    id("matsumo.primitive.nexus.publish")
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false

    // Others
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktorfit) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ksp) apply false
}
