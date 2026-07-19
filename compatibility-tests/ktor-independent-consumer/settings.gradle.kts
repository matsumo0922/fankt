pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }

    repositories {
        val fanktRepository = providers.gradleProperty("fanktRepository").orNull
            ?: error("-PfanktRepository must point to the isolated fankt Maven repository")
        exclusiveContent {
            forRepository {
                maven {
                    name = "isolatedFankt"
                    url = uri(fanktRepository)
                }
            }
            filter {
                includeGroup("me.matsumo.fankt")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "ktor-independent-consumer"
