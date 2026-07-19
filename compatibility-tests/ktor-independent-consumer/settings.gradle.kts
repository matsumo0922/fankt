pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        val fanktRepository = providers.gradleProperty("fanktRepository").orNull
            ?: error("-PfanktRepository must point to the isolated fankt Maven repository")
        maven(fanktRepository)
        google()
        mavenCentral()
    }
}

rootProject.name = "ktor-independent-consumer"
