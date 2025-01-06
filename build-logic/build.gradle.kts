plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

kotlin {
    sourceSets.all {
        languageSettings {
            languageVersion = "2.0"
        }
    }
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.room.gradlePlugin)
    implementation(libs.secret.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.build.konfig.gradlePlugin)
    implementation(libs.gms.services)
    implementation(libs.gms.oss)
    implementation(libs.maven.publish.gradlePlugin)
    implementation(libs.nexus.publish.gradlePlugin)
}

gradlePlugin {
    plugins {
        // KMP
        register("KmpPlugin") {
            id = "matsumo.primitive.kmp.common"
            implementationClass = "primitive.kmp.KmpCommonPlugin"
        }
        register("KmpAndroidPlugin") {
            id = "matsumo.primitive.kmp.android"
            implementationClass = "primitive.kmp.KmpAndroidPlugin"
        }
        register("KmpIosPlugin") {
            id = "matsumo.primitive.kmp.ios"
            implementationClass = "primitive.kmp.KmpIosPlugin"
        }
        register("KmpAndroidCompose") {
            id = "matsumo.primitive.kmp.compose"
            implementationClass = "primitive.kmp.KmpComposePlugin"
        }

        // Android
        register("AndroidCommonPlugin") {
            id = "matsumo.primitive.android.common"
            implementationClass = "primitive.android.AndroidCommonPlugin"
        }
        register("KmpAndroidApplication") {
            id = "matsumo.primitive.android.application"
            implementationClass = "primitive.android.AndroidApplicationPlugin"
        }
        register("KmpAndroidLibrary") {
            id = "matsumo.primitive.android.library"
            implementationClass = "primitive.android.AndroidLibraryPlugin"
        }
        register("AndroidComposePlugin") {
            id = "matsumo.primitive.android.compose"
            implementationClass = "primitive.android.AndroidComposePlugin"
        }

        // Libraries
        register("DetektPlugin") {
            id = "matsumo.primitive.detekt"
            implementationClass = "primitive.DetektPlugin"
        }
        register("KtorfitPlugin") {
            id = "matsumo.primitive.ktorfit"
            implementationClass = "primitive.KtrofitPlugin"
        }
        register("RoomPlugin") {
            id = "matsumo.primitive.room"
            implementationClass = "primitive.RoomPlugin"
        }

        // Publishing
        register("NexusPublishPlugin") {
            id = "matsumo.primitive.nexus.publish"
            implementationClass = "primitive.publish.NexusPublishPlugin"
        }
        register("MavenPublishPlugin") {
            id = "matsumo.primitive.maven.publish"
            implementationClass = "primitive.publish.MavenPublishPlugin"
        }
    }
}
