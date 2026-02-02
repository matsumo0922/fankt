package primitive

import me.matsumo.fankt.library
import me.matsumo.fankt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import primitive.kmp.kotlin

class KtrofitPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("de.jensklingenberg.ktorfit")

            kotlin {
                sourceSets.commonMain.dependencies {
                    implementation(libs.library("ktorfit"))
                }
            }
        }
    }
}
