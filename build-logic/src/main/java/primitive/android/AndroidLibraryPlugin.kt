package primitive.android
import com.android.build.gradle.LibraryExtension
import me.matsumo.fankt.androidLibrary
import me.matsumo.fankt.libs
import me.matsumo.fankt.setupAndroid
import me.matsumo.fankt.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
            }

            androidLibrary {
                setupAndroid()
            }

            extensions.configure<LibraryExtension> {
                compileSdk = libs.version("compileSdk").toInt()
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
                buildFeatures.viewBinding = true
            }
        }
    }
}
