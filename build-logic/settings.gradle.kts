pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

// Gradle の依存解決・version catalog DSL に対する IDE の UnstableApiUsage 検査を、この設定ブロックだけで抑制する。
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
