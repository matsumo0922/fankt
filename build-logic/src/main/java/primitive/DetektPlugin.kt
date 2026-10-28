package primitive
import me.matsumo.fankt.configureDetekt
import me.matsumo.fankt.library
import me.matsumo.fankt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** 共通の静的解析設定と追加ルールを適用する Gradle plugin。 */
class DetektPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("dev.detekt")

            configureDetekt()

            dependencies {
                "detektPlugins"(libs.library("detekt-ktlint-wrapper"))
                "detektPlugins"(libs.library("compose-rules-detekt"))
            }
        }
    }
}
