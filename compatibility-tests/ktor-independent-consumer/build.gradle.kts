import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

abstract class VerifyPublishedConsumerTask : DefaultTask() {
    @get:Input
    abstract val compileComponents: ListProperty<String>

    @get:Input
    abstract val runtimeComponents: ListProperty<String>

    @get:Input
    abstract val fanktVersion: Property<String>

    @get:Input
    abstract val selectedKtorVersion: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val resolvedFanktArtifacts: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localFanktRepository: DirectoryProperty

    @TaskAction
    fun verify() {
        val resolvedCompileComponents = compileComponents.get()
        val expectedFanbox = "me.matsumo.fankt:fanbox:${fanktVersion.get()}"
        check(expectedFanbox in resolvedCompileComponents) {
            "The fixture did not resolve the isolated fanbox publication"
        }
        val compileKtorComponents = resolvedCompileComponents.filter { it.startsWith("io.ktor:") }
        check(compileKtorComponents.isEmpty()) {
            "Ktor leaked onto the published consumer compile classpath:\n" +
                compileKtorComponents.joinToString("\n")
        }

        val selectedKtorComponents = runtimeComponents.get().filter { it.startsWith("io.ktor:") }
        check(selectedKtorComponents.isNotEmpty()) { "The fixture did not resolve runtime Ktor modules" }
        check(selectedKtorComponents.all { it.endsWith(":${selectedKtorVersion.get()}") }) {
            "The consumer-selected Ktor version was not honored:\n" +
                selectedKtorComponents.joinToString("\n")
        }

        val resolvedArtifacts = resolvedFanktArtifacts.files
        check(resolvedArtifacts.isNotEmpty()) { "No me.matsumo.fankt compile artifacts were resolved" }
        val localArtifacts = localFanktRepository.get().asFile
            .walkTopDown()
            .filter { localFile ->
                localFile.isFile && localFile.parentFile.name == fanktVersion.get()
            }
            .toList()
        check(localArtifacts.isNotEmpty()) {
            "The isolated repository contains no me.matsumo.fankt:${fanktVersion.get()} artifacts"
        }
        val unmatchedArtifacts = resolvedArtifacts.filterNot { resolvedArtifact ->
            localArtifacts.any { localArtifact ->
                localArtifact.extension == resolvedArtifact.extension &&
                    localArtifact.length() == resolvedArtifact.length() &&
                    localArtifact.sha256().contentEquals(resolvedArtifact.sha256())
            }
        }
        check(unmatchedArtifacts.isEmpty()) {
            "Resolved fankt artifacts do not match the isolated local publication:\n" +
                unmatchedArtifacts.joinToString("\n")
        }
    }

    private fun File.sha256(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val consumerKtorVersion = "3.2.2"
val publishedFanktVersion = libs.versions.versionName

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.ktor") {
            useVersion(consumerKtorVersion)
            because("the consumer owns selection of a runtime-compatible Ktor version")
        }
    }
}

android {
    namespace = "me.matsumo.fankt.compatibility"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(publishedFanktVersion.map { version -> "me.matsumo.fankt:fanbox:$version" })
}

tasks.register<VerifyPublishedConsumerTask>("verifyKtorSelection") {
    group = "verification"
    description = "Compiles without Ktor while selecting a compatible Ktor version for runtime"
    dependsOn("compileReleaseKotlin")

    val releaseCompileClasspath = configurations.named("releaseCompileClasspath")
    val releaseRuntimeClasspath = configurations.named("releaseRuntimeClasspath")
    compileComponents.set(releaseCompileClasspath.map(::componentIdentities))
    runtimeComponents.set(releaseRuntimeClasspath.map(::componentIdentities))
    fanktVersion.set(publishedFanktVersion)
    selectedKtorVersion.set(consumerKtorVersion)
    resolvedFanktArtifacts.from(
        releaseCompileClasspath.map { configuration ->
            configuration.incoming.artifacts.artifacts.mapNotNull { artifact ->
                val component = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                    ?: return@mapNotNull null
                artifact.file.takeIf { component.group == "me.matsumo.fankt" }
            }
        },
    )
    localFanktRepository.set(
        layout.dir(
            providers.gradleProperty("fanktRepository").map { repository ->
                File(repository).canonicalFile.resolve("me/matsumo/fankt")
            },
        ),
    )
}

fun componentIdentities(configuration: Configuration): List<String> =
    configuration.incoming.resolutionResult.allComponents.map { component ->
        component.moduleVersion?.let { "${it.group}:${it.name}:${it.version}" }
            ?: component.id.displayName
    }
