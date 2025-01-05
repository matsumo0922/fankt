package primitive.kmp
import me.matsumo.fankt.androidExt
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class KmpAndroidPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            kotlin {
                androidTarget {
                    compilations.all {
                        kotlinOptions {
                            jvmTarget = JavaVersion.VERSION_17.toString()
                        }

                        compileTaskProvider.configure {
                            compilerOptions {
                                freeCompilerArgs.addAll("-P", "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=me.matsumo.fankt.domain.Parcelize")
                            }
                        }
                    }
                }
            }

            androidExt {
                sourceSets {
                    getByName("main") {
                        manifest.srcFile("src/androidMain/AndroidManifest.xml")
                        res.srcDirs("src/androidMain/res")
                    }
                }
            }
        }
    }
}
