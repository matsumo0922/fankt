package primitive.publish

import me.matsumo.fankt.androidLibrary
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.SigningExtension

class MavenPublishPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("maven-publish")
                apply("signing")
            }

            androidLibrary {
                publishing {
                    singleVariant("release") {
                        withSourcesJar()
                        withJavadocJar()
                    }
                }
            }

            configurePublishing()
            configureSigning()
        }
    }

    private fun Project.configurePublishing() {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("aar") {
                    afterEvaluate {
                        from(components["release"])
                    }

                    pom {
                        name = project.name
                        description = "Unofficial API wrapper for pixivFANBOX and Fantia"
                        url = "https://github.com/matsumo0922/fankt"

                        scm {
                            connection = "scm:git@github.com:matsumo0922/fankt.git"
                            developerConnection = "scm:git@github.com:matsumo0922/fankt.git"
                            url = "scm:git@github.com:matsumo0922/fankt.git"
                        }

                        licenses {
                            license {
                                name = "GNU General Public License v3.0"
                                url = "https://www.gnu.org/licenses/gpl-3.0.html"
                            }
                        }

                        developers {
                            developer {
                                id = "matsumo0922"
                                name = "Daichi Matsumoto"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Project.configureSigning() {
        extensions.configure<SigningExtension> {
            useGpgCmd()
            sign(extensions.getByType<PublishingExtension>().publications["aar"])
        }
    }
}
