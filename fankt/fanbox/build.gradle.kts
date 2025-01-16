plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.android.common")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.ktorfit")
    id("matsumo.primitive.room")
    id("matsumo.primitive.detekt")
    id("matsumo.primitive.maven.publish")
}

android {
    namespace = "me.matsumo.fankt.fanbox"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.infra.api)
            api(libs.bundles.ktor)

            implementation(libs.ktorfit)
            implementation(libs.ksoup)
        }

        androidMain.dependencies {
            implementation(libs.androidx.startup)
            api(libs.ktor.okhttp)
        }

        iosMain.dependencies {
            api(libs.ktor.darwin)
        }
    }
}
