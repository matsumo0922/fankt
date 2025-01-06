package primitive.publish

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.plugins.signing.SigningExtension

class MavenPublishPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("maven-publish")
                apply("signing")
                apply("com.vanniktech.maven.publish")
                apply("org.jetbrains.dokka")
                apply("org.jetbrains.dokka-javadoc")
            }

            afterEvaluate {
                tasks.filter { task ->
                    task.name.contains("SourcesJar", true)
                }.forEach { task ->
                    task.dependsOn("kspCommonMainKotlinMetadata")
                }
            }

            configureMavenPublish()
            configureSigning()
        }
    }

    private fun Project.configureMavenPublish() {
        extensions.configure<MavenPublishBaseExtension> {
            configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

            val hasUserNameFromProject = project.hasProperty("mavenCentralUsername")
            val hasUserNameFromEnv = System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername") != null

            if (hasUserNameFromProject || hasUserNameFromEnv) {
                signAllPublications()
            }

            coordinates(
                groupId = group.toString(),
                version = version.toString(),
                artifactId = "fankt",
            )

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

    private fun Project.configureSigning() {
        extensions.configure<SigningExtension> {
            val hasUserNameFromProject = project.hasProperty("mavenCentralUsername")
            val hasUserNameFromEnv = System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername") != null

            if (hasUserNameFromProject || hasUserNameFromEnv) {
                useGpgCmd()
            }
        }
    }
}
