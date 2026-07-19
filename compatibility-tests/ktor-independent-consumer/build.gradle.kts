plugins {
    id("com.android.library") version "8.12.0"
    id("org.jetbrains.kotlin.android") version "2.2.10"
}

val selectedKtorVersion = "3.2.2"

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.ktor") {
            useVersion(selectedKtorVersion)
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
    implementation("me.matsumo.fankt:fanbox:0.1.0")
    implementation("io.ktor:ktor-client-core:$selectedKtorVersion")
}

tasks.register("verifyKtorSelection") {
    group = "verification"
    description = "Compiles against published fankt while selecting a compatible consumer Ktor version"
    dependsOn("compileReleaseKotlin")

    val selectedComponents = configurations.named("releaseCompileClasspath").map { configuration ->
        configuration.incoming.resolutionResult.allComponents.map { component ->
            component.moduleVersion?.let { "${it.group}:${it.name}:${it.version}" }
                ?: component.id.displayName
        }
    }
    inputs.property("selectedComponents", selectedComponents)

    doLast {
        val components = selectedComponents.get()
        check(components.any { it == "me.matsumo.fankt:fanbox:0.1.0" }) {
            "The fixture did not resolve the isolated fanbox publication"
        }
        val selectedKtorComponents = components.filter { it.startsWith("io.ktor:") }
        check(selectedKtorComponents.isNotEmpty()) { "The fixture did not resolve its explicit Ktor dependency" }
        check(selectedKtorComponents.all { it.endsWith(":$selectedKtorVersion") }) {
            "The consumer-selected Ktor version was not honored:\n${selectedKtorComponents.joinToString("\n")}"
        }
    }
}
