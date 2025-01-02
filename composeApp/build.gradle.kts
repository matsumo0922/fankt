plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.kmp.android.application")
    id("matsumo.primitive.kmp.android.compose")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "me.matsumo.fankt.sample"

    // https://youtrack.jetbrains.com/issue/CMP-6707/Compose-Multiplatform-Resources-and-Kotlin-2.1.0-Beta1-SyncComposeResourcesForIosTask-configuration-failure
    tasks.findByName("checkSandboxAndWriteProtection")?.dependsOn("syncComposeResourcesForIos")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":fankt"))

            implementation(libs.bundles.infra.api)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        androidMain.dependencies {
            implementation(libs.bundles.ui.android.api)
            implementation(libs.androidx.core.splashscreen)
        }
    }
}
