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
    id("matsumo.primitive.detekt")
    id("matsumo.primitive.maven.publish")
    alias(libs.plugins.zipline)
}

android {
    namespace = "me.matsumo.fankt.fanbox"
}

// The ziplineTest target runs the host's tests on the JVM; it is not meant to be resolved as a
// dependency. Disabling their publication tasks keeps the artifacts out of the
// repository, but the root Kotlin Multiplatform module still lists them, and a consumer following
// one of those references fails to resolve this library at all. So the variants are stripped from
// the generated root module as well.
tasks.matching { task -> task.name.contains("ZiplineTestPublication") }.configureEach {
    enabled = false
}

tasks.register<StripUnpublishedVariantsTask>("stripUnpublishedRootVariants") {
    val metadataTask = tasks.named<GenerateModuleMetadata>(
        "generateMetadataFileForKotlinMultiplatformPublication",
    )
    dependsOn(metadataTask)
    moduleFile.set(metadataTask.flatMap { it.outputFile })
    unpublishedTargetNames.set(listOf("ziplineTest"))
}

tasks.named("generateMetadataFileForKotlinMultiplatformPublication") {
    finalizedBy("stripUnpublishedRootVariants")
}

kotlin {
    // The host's tests run on the JVM, where QuickJS loads the guest bundle.
    jvm("ziplineTest")

    sourceSets {
        all {
            languageSettings.optIn("me.matsumo.fankt.fanbox.InternalFanboxApi")
        }

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
            api(project(":fankt:fanbox-core"))
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

// The QuickJS tests load the guest bundle that :fankt:fanbox-guest builds.
tasks.named("ziplineTestTest") {
    dependsOn(":fankt:fanbox-guest:compileProductionExecutableKotlinJsZipline")
}
