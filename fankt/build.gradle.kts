plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.kmp.android.library")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.detekt")
}

android {
    namespace = "me.matsumo.fankt"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.infra.api)
            implementation(libs.bundles.ktor)
            implementation(libs.ktorfit)
            implementation(libs.ksoup)
        }

        androidMain.dependencies {
            api(libs.ktor.okhttp)
        }

        iosMain.dependencies {
            api(libs.ktor.darwin)
        }
    }
}
