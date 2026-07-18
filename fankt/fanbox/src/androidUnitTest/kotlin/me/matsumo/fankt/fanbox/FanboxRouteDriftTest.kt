package me.matsumo.fankt.fanbox

import io.ktor.http.Url
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FanboxRouteDriftTest {

    @Test
    fun productionRouteDeclarationsStayCoveredByDiagnosticResolver() {
        val sourceDirectory = locateApiSourceDirectory()
        val allApiFiles = sourceDirectory
            .listFiles { file ->
                file.name.startsWith("Fanbox") &&
                    file.name.endsWith("Api.kt")
            }
            ?.sortedBy(File::getName)
            .orEmpty()

        assertEquals(4, allApiFiles.size, "the diagnostic inventory must cover all API interfaces")

        val apiRoutes = allApiFiles.flatMap(::readDeclaredRoutes)
        assertEquals(29, apiRoutes.size, "the production API route declaration inventory changed")
        assertEquals(28, apiRoutes.toSet().size, "the production API route inventory changed")
        assertEquals(
            mapOf("plan.listSupporting" to 2),
            apiRoutes.groupingBy { it }.eachCount().filterValues { it > 1 },
            "only the strict and tolerant plan routes may share an endpoint",
        )

        apiRoutes.forEach { declaration ->
            val url = declaration.toApiUrl()
            val target = FanboxExceptionFactory.target(FanboxDiagnosticSource.LibraryGenerated, url)
            val expectedEndpoint = if (declaration == "https://www.fanbox.cc/") {
                "homepage"
            } else {
                declaration
            }

            assertEquals(expectedEndpoint, target.endpoint, "resolver label drifted for $declaration")
            assertTrue(target.retainResponseFragment, "known route lost bounded diagnostics: $declaration")
        }

        val unknownApiTarget = FanboxExceptionFactory.target(
            FanboxDiagnosticSource.LibraryGenerated,
            Url("https://api.fanbox.cc/route-added-without-resolver"),
        )
        assertEquals("custom-request", unknownApiTarget.endpoint)
        assertFalse(unknownApiTarget.retainResponseFragment)

        assertFalse(sourceDirectory.resolve("FanboxDownloadApi.kt").exists())
    }

    private fun readDeclaredRoutes(file: File): List<String> = ROUTE_ANNOTATION
        .findAll(file.readText())
        .map { it.groupValues[2] }
        .toList()

    private fun String.toApiUrl(): Url = if (startsWith("http")) {
        Url(this)
    } else {
        Url("https://api.fanbox.cc/$this")
    }

    private fun locateApiSourceDirectory(): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = directory.resolve(
                "fankt/fanbox/src/commonMain/kotlin/me/matsumo/fankt/fanbox/datasource",
            )
            if (candidate.isDirectory) return candidate
            directory = directory.parentFile ?: error("Unable to locate FANBOX API source declarations")
        }
    }

    private companion object {
        val ROUTE_ANNOTATION = Regex("""@(GET|POST)\s*\(\s*"([^"]+)"\s*\)""")
    }
}
