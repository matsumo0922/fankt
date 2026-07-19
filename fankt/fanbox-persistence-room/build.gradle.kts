plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.android.common")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.room")
    id("matsumo.primitive.detekt")
    id("matsumo.primitive.maven.publish")
}

android {
    namespace = "me.matsumo.fankt.fanbox.persistence.room"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":fankt:fanbox"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidUnitTest.dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
            implementation(libs.sqlite.jdbc)
        }
    }
}
