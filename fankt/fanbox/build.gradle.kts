import app.cash.zipline.loader.SignatureAlgorithmId
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Removes variants belonging to targets whose publication tasks are disabled.
 *
 * Kotlin Multiplatform writes every target into the root module, and its root component is not an
 * `AdhocComponentWithVariants`, so Gradle's variant-skipping API cannot reach them. Left in place,
 * the root module points at modules that never reach the repository and a consumer following one
 * of those references fails to resolve the library.
 */
abstract class StripUnpublishedVariantsTask : DefaultTask() {
    @get:InputFile
    abstract val moduleFile: RegularFileProperty

    @get:Input
    abstract val unpublishedTargetNames: ListProperty<String>

    @TaskAction
    fun strip() {
        val file = moduleFile.get().asFile

        // Gradle が生成する module metadata の JSON object を、更新可能な文字列 key の map として扱う。
        @Suppress("UNCHECKED_CAST")
        val module = JsonSlurper().parse(file) as MutableMap<String, Any?>
        val variants = module["variants"] as? MutableList<*> ?: return
        val targets = unpublishedTargetNames.get()

        val removed = variants.removeAll { variant ->
            // variant は同じ module metadata の JSON object で、name の値は String として別途検証する。
            @Suppress("UNCHECKED_CAST")
            val name = (variant as? Map<String, Any?>)?.get("name") as? String ?: return@removeAll false
            targets.any(name::startsWith)
        }
        check(removed) { "No unpublished target variant was found in $file; the target names may be stale" }

        file.writeText(
            text = JsonOutput.prettyPrint(JsonOutput.toJson(module)),
        )
    }
}

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
    namespace = "me.matsumo.fankt.fanbox"
}

zipline {
    mainFunction.set("me.matsumo.fankt.fanbox.guest.launchZipline")
    // The delivery workflow passes the commit it builds, which consumers receive when the guest loads.
    version.set(providers.gradleProperty("fanboxZiplineVersion"))

    // The delivery workflow passes the key from a repository secret. Local builds and pull request
    // CI have no secret, and they build an unsigned manifest rather than failing: the loader
    // requires a signature, so an unsigned bundle cannot be executed by a consumer even if it
    // escapes. The workflow separately refuses to publish a manifest that carries no signature.
    // The key name switches to the recovery key when the primary key is lost. See the README.
    val signingPrivateKeyHex = providers.gradleProperty("fanboxZiplineSigningPrivateKeyHex")
    val signingKeyName = providers.gradleProperty("fanboxZiplineSigningKeyName").orElse("fanboxGuest")
    if (signingPrivateKeyHex.isPresent) {
        signingKeys.create(signingKeyName.get()) {
            algorithmId.set(SignatureAlgorithmId.Ed25519)
            privateKeyHex.set(signingPrivateKeyHex)
        }
    }
}

// The guest and ziplineTest targets build a bundle and run tests and run tests; they are not meant
// to be resolved as dependencies. Disabling their publication tasks keeps the artifacts out of the
// repository, but the root Kotlin Multiplatform module still lists them, and a consumer following
// one of those references fails to resolve this library at all. So the variants are stripped from
// the generated root module as well.
tasks.matching { task ->
    task.name.contains("GuestPublication") || task.name.contains("ZiplineTestPublication")
}.configureEach {
    enabled = false
}

tasks.register<StripUnpublishedVariantsTask>("stripUnpublishedRootVariants") {
    val metadataTask = tasks.named<GenerateModuleMetadata>(
        "generateMetadataFileForKotlinMultiplatformPublication",
    )
    dependsOn(metadataTask)
    moduleFile.set(metadataTask.flatMap { it.outputFile })
    unpublishedTargetNames.set(listOf("guest", "ziplineTest"))
}

tasks.named("generateMetadataFileForKotlinMultiplatformPublication") {
    finalizedBy("stripUnpublishedRootVariants")
}

// Two Kotlin/JS targets share a platform type, so consumers need an attribute to tell their
// variants apart.
val fanboxJsTargetAttribute: Attribute<String> = Attribute.of("me.matsumo.fankt.jsTarget", String::class.java)

kotlin {
    // The guest bundle needs an executable binary with an entry point, and the existing `js` target
    // publishes a library. Zipline's serve task name omits the binary kind, so declaring both on one
    // target registers it twice and fails configuration. A second target keeps them apart.
    js("guest", org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType.IR) {
        nodejs()
        binaries.executable()
        attributes.attribute(fanboxJsTargetAttribute, "guest")
    }

    jvm("ziplineTest")

    targets.named("js") {
        attributes.attribute(fanboxJsTargetAttribute, "library")
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val clientMain by creating { dependsOn(commonMain) }
        val clientTest by creating { dependsOn(commonTest) }
        getByName("ziplineTestMain") { dependsOn(clientMain) }
        val ziplineTestTest by getting {
            kotlin.srcDir("src/clientTest/zipline")
        }
        val androidMain by getting { dependsOn(clientMain) }
        val androidUnitTest by getting { dependsOn(clientTest) }
        val iosMain by getting { dependsOn(clientMain) }
        getByName("iosTest") { dependsOn(clientTest) }

        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.core)

            // The bridge service interface is shared by the host and the guest, so its Zipline
            // dependency reaches every published target.
            implementation(libs.zipline)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)
            // The guest extracts the homepage metadata, so the HTML parser ships in the bundle.
            implementation(libs.ksoup)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        clientMain.dependencies {
            implementation(libs.napier)
            implementation(libs.bundles.ktor)
            implementation(libs.zipline.loader)
        }

        clientTest.dependencies {
            implementation(libs.ktor.mock)
        }

        ziplineTestTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.mock)
        }

        androidMain.dependencies {
            implementation(libs.ktor.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
    }
}

tasks.matching { task ->
    task.name in setOf(
        "testDebugUnitTest",
        "testReleaseUnitTest",
        "iosSimulatorArm64Test",
        "iosX64Test",
        "ziplineTestTest",
    )
}.configureEach {
    dependsOn("compileProductionExecutableKotlinGuestZipline")
}
