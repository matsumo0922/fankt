import java.util.zip.ZipFile

plugins {
    id("matsumo.primitive.kmp.common")
    id("matsumo.primitive.android.library")
    id("matsumo.primitive.android.common")
    id("matsumo.primitive.kmp.android")
    id("matsumo.primitive.kmp.ios")
    id("matsumo.primitive.ktorfit")
    id("matsumo.primitive.detekt")
    id("matsumo.primitive.maven.publish")
}

tasks.register("verifyPersistenceBoundary") {
    group = "verification"
    description = "Verifies that the core FANBOX artifact has no optional persistence dependencies"
    dependsOn("bundleReleaseAar")

    doLast {
        val coreConfigurations = configurations.filter { configuration ->
            configuration.isCanBeResolved && (
                configuration.name == "androidReleaseCompileClasspath" ||
                    configuration.name == "commonMainResolvableDependenciesMetadata" ||
                    configuration.name.matches(Regex("ios.*CompileKlibraries"))
                )
        }
        check(coreConfigurations.any { it.name == "androidReleaseCompileClasspath" }) {
            "Android release dependency configuration was not inspected"
        }
        check(coreConfigurations.any { it.name.matches(Regex("ios.*CompileKlibraries")) }) {
            "iOS dependency configurations were not inspected"
        }

        val forbiddenMarkers = listOf(
            "androidx.room:",
            "androidx.sqlite:",
            "androidx.startup:",
            "project :fankt:fanbox-persistence-room",
        )
        val violations = coreConfigurations.flatMap { configuration ->
            configuration.incoming.resolutionResult.allComponents.mapNotNull { component ->
                val identity = component.id.displayName.lowercase()
                forbiddenMarkers.firstOrNull(identity::contains)?.let { marker ->
                    "${configuration.name}: $identity ($marker)"
                }
            }
        }
        check(violations.isEmpty()) {
            "Core FANBOX dependency boundary violations:\n${violations.joinToString("\n")}"
        }

        val manifests = fileTree(layout.buildDirectory.dir("outputs/aar")) {
            include("*.aar")
        }.files
        check(manifests.isNotEmpty()) { "No release AAR was produced for manifest verification" }
        manifests.forEach { aar ->
            val manifest = ZipFile(aar).use { archive ->
                val entry = checkNotNull(archive.getEntry("AndroidManifest.xml"))
                archive.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            check("androidx.startup.InitializationProvider" !in manifest)
            check("FanktInitializer" !in manifest)
        }
    }
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

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.mock)
        }

        androidMain.dependencies {
            api(libs.ktor.okhttp)
        }

        androidUnitTest.dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
        }

        iosMain.dependencies {
            api(libs.ktor.darwin)
        }
    }
}
