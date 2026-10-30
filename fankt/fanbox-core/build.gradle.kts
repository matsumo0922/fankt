plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.android.common")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.kmp.js")
    id("matsumo.primitive.detekt")
    id("matsumo.primitive.maven.publish")
    alias(libs.plugins.zipline)
}

android {
    namespace = "me.matsumo.fankt.fanbox.core"
}

kotlin {
    // The host's QuickJS tests run on the JVM.
    jvm()

    sourceSets {
        all {
            languageSettings.optIn("me.matsumo.fankt.fanbox.InternalFanboxApi")
        }

        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.core)
            // The bridge service interface extends ZiplineService, so Zipline reaches every consumer.
            api(libs.zipline)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)
            // The guest extracts the homepage metadata, so the HTML parser ships in the bundle.
            implementation(libs.ksoup)
        }
    }
}
