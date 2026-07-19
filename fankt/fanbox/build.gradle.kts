import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

private fun ResolvedComponentResult.allComponentIdentities(): List<String> {
    val identities = linkedSetOf<String>()
    val pending = ArrayDeque<ResolvedComponentResult>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        val component = pending.removeFirst()
        if (identities.add(component.id.displayName)) {
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { pending.add(it.selected) }
        }
    }
    return identities.toList()
}

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

abstract class VerifyKtorBoundaryTask : DefaultTask() {
    @get:Input
    abstract val apiDependencyDeclarations: ListProperty<String>

    @get:Input
    abstract val requiredAndroidRuntimeKtorDependencies: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val abiFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidModuleMetadata: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val forbiddenApiDeclarations = apiDependencyDeclarations.get().filter { "io.ktor:" in it }
        check(forbiddenApiDeclarations.isEmpty()) {
            "Ktor must be an implementation dependency, but API declarations were found:\n" +
                forbiddenApiDeclarations.joinToString("\n")
        }

        val checkedAbiFiles = abiFiles.files.filter { it.isFile }
        check(checkedAbiFiles.isNotEmpty()) {
            "No checked-in fanbox ABI files were found; run :fankt:fanbox:updateLegacyAbi"
        }
        val leakingAbiFiles = checkedAbiFiles.filter { abi -> "io.ktor" in abi.readText() }
        check(leakingAbiFiles.isEmpty()) {
            "Ktor types leaked into the public fanbox ABI:\n" +
                leakingAbiFiles.joinToString("\n") { it.relativeTo(project.projectDir).path }
        }

        val metadataFiles = androidModuleMetadata.files.filter { it.isFile }
        check(metadataFiles.isNotEmpty()) { "No Android Gradle module metadata was generated" }
        val apiViolations = metadataFiles.flatMap { metadataFile ->
            val metadata = JsonSlurper().parse(metadataFile) as Map<*, *>
            val variants = metadata["variants"] as? List<*> ?: emptyList<Any>()
            variants.flatMap variantLoop@{ variantValue ->
                val variant = variantValue as? Map<*, *> ?: return@variantLoop emptyList()
                val attributes = variant["attributes"] as? Map<*, *> ?: emptyMap<Any, Any>()
                if (attributes["org.gradle.usage"] != "java-api") return@variantLoop emptyList()
                val variantName = variant["name"].toString()
                val dependencies = variant["dependencies"] as? List<*> ?: emptyList<Any>()
                dependencies.mapNotNull dependencyLoop@{ dependencyValue ->
                    val dependency = dependencyValue as? Map<*, *> ?: return@dependencyLoop null
                    if (dependency["group"] == "io.ktor") {
                        "${metadataFile.name}: $variantName -> ${dependency["group"]}:${dependency["module"]}"
                    } else {
                        null
                    }
                }
            }
        }
        check(apiViolations.isEmpty()) {
            "Ktor dependencies leaked into Android API publication metadata:\n" +
                apiViolations.joinToString("\n")
        }

        val publishedRuntimeKtorDependencies = metadataFiles.flatMap { metadataFile ->
            val metadata = JsonSlurper().parse(metadataFile) as Map<*, *>
            val variants = metadata["variants"] as? List<*> ?: emptyList<Any>()
            variants.flatMap variantLoop@{ variantValue ->
                val variant = variantValue as? Map<*, *> ?: return@variantLoop emptyList()
                val attributes = variant["attributes"] as? Map<*, *> ?: emptyMap<Any, Any>()
                if (attributes["org.gradle.usage"] != "java-runtime") return@variantLoop emptyList()
                val dependencies = variant["dependencies"] as? List<*> ?: emptyList<Any>()
                dependencies.mapNotNull dependencyLoop@{ dependencyValue ->
                    val dependency = dependencyValue as? Map<*, *> ?: return@dependencyLoop null
                    if (dependency["group"] == "io.ktor") {
                        "${dependency["group"]}:${dependency["module"]}"
                    } else {
                        null
                    }
                }
            }
        }.toSet()
        val missingRuntimeDependencies =
            requiredAndroidRuntimeKtorDependencies.get().toSet() - publishedRuntimeKtorDependencies
        check(missingRuntimeDependencies.isEmpty()) {
            "Android runtime publication metadata is missing required Ktor dependencies:\n" +
                missingRuntimeDependencies.joinToString("\n")
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
        val configurationName = configuration.name
        dependencyIdentities.addAll(
            configuration.incoming.resolutionResult.rootComponent.map { rootComponent ->
                rootComponent.allComponentIdentities().map { identity ->
                    "$configurationName: ${identity.lowercase()}"
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
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.infra.api)
            implementation(libs.bundles.ktor)

            implementation(libs.ktorfit)
            implementation(libs.ksoup)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.mock)
        }

        androidMain.dependencies {
            implementation(libs.ktor.okhttp)
        }

        androidUnitTest.dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
        }

        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
    }
}

val sourceSetApiConfigurations = configurations.matching { configuration ->
    configuration.name.endsWith("MainApi")
}
val androidRuntimeImplementationConfigurations = configurations.matching { configuration ->
    configuration.name == "commonMainImplementation" || configuration.name == "androidMainImplementation"
}
val androidMetadataTasks = tasks.withType<GenerateModuleMetadata>().matching {
    it.name == "generateMetadataFileForAndroidReleasePublication"
}

val verifyKtorBoundary = tasks.register<VerifyKtorBoundaryTask>("verifyKtorBoundary") {
    group = "verification"
    description = "Verifies that Ktor remains outside the public FANBOX API boundary"
    dependsOn("checkLegacyAbi", androidMetadataTasks)

    abiFiles.from(layout.projectDirectory.dir("api").asFileTree.matching { include("**/*.api") })
    androidModuleMetadata.from(androidMetadataTasks)
}

sourceSetApiConfigurations.configureEach {
    val apiConfiguration = this
    verifyKtorBoundary.configure {
        apiDependencyDeclarations.addAll(
            provider {
                apiConfiguration.dependencies.map { dependency ->
                    "${apiConfiguration.name}: ${dependency.group}:${dependency.name}"
                }
            },
        )
    }
}

androidRuntimeImplementationConfigurations.configureEach {
    val implementationConfiguration = this
    verifyKtorBoundary.configure {
        requiredAndroidRuntimeKtorDependencies.addAll(
            provider {
                implementationConfiguration.dependencies.mapNotNull { dependency ->
                    dependency.group
                        ?.takeIf { it == "io.ktor" }
                        ?.let { group -> "$group:${dependency.name}" }
                }
            },
        )
    }
}

tasks.named("check") {
    dependsOn("verifyKtorBoundary")
}
