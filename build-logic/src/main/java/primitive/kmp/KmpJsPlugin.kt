package primitive.kmp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType

class KmpJsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            kotlin {
                js(KotlinJsCompilerType.IR) {
                    nodejs()
                    binaries.library()
                }
            }
        }
    }
}
