import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

abstract class VerifyPersistenceBoundaryTask : DefaultTask() {
    @get:Input
    abstract val inspectedConfigurationNames: ListProperty<String>

    @get:Input
    abstract val dependencyIdentities: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val aarFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val configurationNames = inspectedConfigurationNames.get()
        check("androidReleaseCompileClasspath" in configurationNames) {
            "Android release dependency configuration was not inspected"
        }
        check(configurationNames.any { it.matches(Regex("ios.*CompileKlibraries")) }) {
            "iOS dependency configurations were not inspected"
        }

        val forbiddenMarkers = listOf(
            "androidx.room:",
            "androidx.sqlite:",
            "androidx.startup:",
            "project :fankt:fanbox-persistence-room",
        )
        val violations = dependencyIdentities.get().mapNotNull { identity ->
            forbiddenMarkers.firstOrNull(identity::contains)?.let { marker -> "$identity ($marker)" }
        }
        check(violations.isEmpty()) {
            "Core FANBOX dependency boundary violations:\n${violations.joinToString("\n")}"
        }

        val manifests = aarFiles.files
        check(manifests.isNotEmpty()) { "No release AAR was produced for manifest verification" }
        manifests.forEach { aar ->
            val manifest = ZipFile(aar).use { archive ->
                val entry = checkNotNull(archive.getEntry("AndroidManifest.xml")) {
                    "${aar.name}: AndroidManifest.xml is missing from the AAR"
                }
                archive.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            check("androidx.startup.InitializationProvider" !in manifest) {
                "${aar.name}: merged AndroidManifest.xml contains androidx.startup.InitializationProvider"
            }
            check("FanktInitializer" !in manifest) {
                "${aar.name}: merged AndroidManifest.xml contains FanktInitializer"
            }
        }
    }
}

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

val persistenceBoundaryConfigurations = configurations.matching { configuration ->
    if (!configuration.isCanBeResolved) {
        false
    } else {
        configuration.name == "androidReleaseCompileClasspath" ||
            configuration.name == "commonMainResolvableDependenciesMetadata" ||
            configuration.name.matches(Regex("ios.*CompileKlibraries"))
    }
}

tasks.register<VerifyPersistenceBoundaryTask>("verifyPersistenceBoundary") {
    group = "verification"
    description = "Verifies that the core FANBOX artifact has no optional persistence dependencies"
    dependsOn("bundleReleaseAar")

    inspectedConfigurationNames.set(persistenceBoundaryConfigurations.names.sorted())
    persistenceBoundaryConfigurations.forEach { configuration ->
        dependencyIdentities.addAll(
            configuration.incoming.artifacts.resolvedArtifacts.map { artifacts ->
                artifacts.map { artifact ->
                    "${configuration.name}: ${artifact.id.componentIdentifier.displayName.lowercase()}"
                }
            },
        )
    }
    aarFiles.from(
        layout.buildDirectory.dir("outputs/aar").map { outputDirectory ->
            outputDirectory.asFileTree.matching { include("*-release.aar") }
        },
    )
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
