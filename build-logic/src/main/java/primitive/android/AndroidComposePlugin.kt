package primitive.android

import me.matsumo.fankt.androidTestImplementation
import me.matsumo.fankt.commonExt
import me.matsumo.fankt.debugImplementation
import me.matsumo.fankt.implementation
import me.matsumo.fankt.library
import me.matsumo.fankt.libs
import me.matsumo.fankt.plugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.plugin("kotlin-compose-compiler").pluginId)
            }

            commonExt {
                buildFeatures.compose = true
            }

            dependencies {
                val bom = libs.library("androidx-compose-bom")

                implementation(project.dependencies.platform(bom))
                implementation(libs.library("androidx-compose-ui-tooling-preview"))
                debugImplementation(libs.library("androidx-compose-ui-tooling"))
                androidTestImplementation(project.dependencies.platform(bom))
            }
        }
    }
}
