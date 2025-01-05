plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.ktorfit")
    id("matsumo.primitive.room")
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
            implementation(libs.androidx.startup)
            implementation(libs.ktor.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
    }
}
