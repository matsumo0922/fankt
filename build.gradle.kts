plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.kmpCompose) apply false
    alias(libs.plugins.kmpComplete) apply false
    alias(libs.plugins.kmpSwiftKlib) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktorfit) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.android) apply false
}
