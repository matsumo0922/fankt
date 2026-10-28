package me.matsumo.fankt

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.report.ReportMergeTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

internal fun Project.configureDetekt() {
    extensions.getByType<DetektExtension>().apply {
        toolVersion.set(libs.version("detekt"))
        parallel.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig.set(true)
        ignoreFailures.set(false)
        autoCorrect.set(false)
    }

    val reportMerge = if ("reportMerge" in rootProject.tasks.names) {
        rootProject.tasks.named<ReportMergeTask>("reportMerge")
    } else {
        rootProject.tasks.register<ReportMergeTask>("reportMerge") {
            output.set(rootProject.layout.buildDirectory.file("reports/detekt/merge.xml"))
        }
    }
    val generatedRoot = layout.buildDirectory.get().asFile

    tasks.withType<Detekt>().configureEach {
        // 型解析ではプラグインが選んだ source set を維持し、生成コードを除外する。
        exclude { it.file.toPath().startsWith(generatedRoot.toPath()) }
    }

    val reportFile = layout.buildDirectory.file("reports/detekt/detekt.xml")
    val analysis = tasks.named<Detekt>("detekt") {
        setSource(project.fileTree(projectDir))
        include("**/*.kt", "**/*.kts")
        exclude("**/resources/**", "**/build/**", "**/.gradle/**")

        reports.checkstyle.outputLocation.set(reportFile)
    }

    if (this == rootProject) {
        analysis.configure { finalizedBy(reportMerge) }
    } else {
        rootProject.tasks.named<Detekt>("detekt").configure { dependsOn(analysis) }
    }

    reportMerge.configure {
        mustRunAfter(analysis)
        // タスク出力の Provider ではなく出力先を登録し、別の解析を実行対象へ加えない。
        input.from(reportFile)
    }
}
