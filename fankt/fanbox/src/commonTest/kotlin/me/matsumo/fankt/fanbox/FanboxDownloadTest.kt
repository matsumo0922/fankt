package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.request.HttpRequestData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FanboxDownloadTest {

    @Test
    fun exactNonJpegUrlIsDeferredAndReportsProgressThroughPublicPath() = runBlocking {
        val payload = "fixture-payload"
        val fixture = createFixture {
            respond(
                content = payload,
                headers = headersOf(HttpHeaders.ContentLength, payload.length.toString()),
            )
        }
        val progress = mutableListOf<Float>()
        val url = "https://downloads.fanbox.cc/files/post/123/archive.zip?download=1"

        val statement = fixture.fanbox.download(url, progress::add)
        assertTrue(fixture.requests.isEmpty())

        assertEquals(payload, statement.execute { response -> response.bodyAsText() })

        assertEquals(1, fixture.requests.size)
        assertEquals(url, fixture.requests.single().url.toString())
        assertEquals("fixture-token", fixture.requests.single().csrfToken)
        assertTrue(fixture.requests.single().cookie.orEmpty().contains("FANBOXSESSID=fixture-session"))
        assertEquals(1f, progress.last())
        assertEquals(5, fixture.clients.size)
        fixture.fanbox.close()
    }

    @Test
    fun observedExternalHostsOmitCsrfAndFanboxCookies() = runBlocking {
        val fixture = createFixture()

        fixture.fanbox.download("https://pixiv.pximg.net/image.png") {}.execute { it.bodyAsText() }
        fixture.fanbox.download("https://fanbox.pixiv.net/images/entry/file.gif") {}.execute { it.bodyAsText() }

        assertEquals(listOf("pixiv.pximg.net", "fanbox.pixiv.net"), fixture.requests.map { it.url.host })
        fixture.requests.forEach { request ->
            assertNull(request.csrfToken)
            assertNull(request.cookie)
        }
        fixture.fanbox.close()
    }

    @Test
    fun invalidDestinationsAreRejectedBeforeCreatingARequest() = runBlocking {
        val fixture = createFixture()
        val invalidUrls = listOf(
            "http://downloads.fanbox.cc/file.zip",
            "https://example.com/file.zip",
            "https://fanbox.cc.evil.example/file.zip",
            "https://notfanbox.cc/file.zip",
            "https://www.pixiv.net/file.zip",
            "https://i.pximg.net/file.zip",
            "https://downloads.fanbox.cc./file.zip",
            "https://user:password@downloads.fanbox.cc/file.zip",
            "not a URL",
        )

        invalidUrls.forEach { url ->
            assertFailsWith<IllegalArgumentException>(url) {
                fixture.fanbox.download(url) {}
            }
        }

        assertTrue(fixture.requests.isEmpty())
        fixture.fanbox.close()
    }

    @Test
    fun uppercaseAllowedHostIsNormalized() = runBlocking {
        val fixture = createFixture()

        fixture.fanbox.download("https://DOWNLOADS.FANBOX.CC/file.psd") {}.execute { it.bodyAsText() }

        assertEquals("downloads.fanbox.cc", fixture.requests.single().url.host.lowercase())
        fixture.fanbox.close()
    }

    @Test
    fun redirectOutsideAllowlistIsRejectedBeforeSecondTransportAndCancelsPriorCall() = runBlocking {
        var firstRequestJob: Job? = null
        val fixture = createFixture { request ->
            if (request.url.host == "downloads.fanbox.cc") {
                firstRequestJob = request.executionContext
                respondRedirect("https://evil.example/file.zip")
            } else {
                error("Disallowed redirect reached transport: ${request.url}")
            }
        }
        val statement = fixture.fanbox.download("https://downloads.fanbox.cc/file.zip") {}

        assertFailsWith<IllegalArgumentException> {
            statement.execute { it.bodyAsText() }
        }

        assertEquals(listOf("downloads.fanbox.cc"), fixture.requests.map { it.url.host })
        assertTrue(firstRequestJob?.isCancelled == true)
        fixture.fanbox.close()
    }

    @Test
    fun allowedRedirectIsRevalidatedAndRemovesCsrfBeforeExternalTransport() = runBlocking {
        val fixture = createFixture { request ->
            if (request.url.host == "downloads.fanbox.cc") {
                respondRedirect("https://pixiv.pximg.net/redirected.png")
            } else {
                respond("redirected")
            }
        }

        val body = fixture.fanbox.download("https://downloads.fanbox.cc/original.png") {}
            .execute { it.bodyAsText() }

        assertEquals("redirected", body)
        assertEquals(listOf("downloads.fanbox.cc", "pixiv.pximg.net"), fixture.requests.map { it.url.host })
        assertEquals("fixture-token", fixture.requests.first().csrfToken)
        assertNull(fixture.requests.last().csrfToken)
        fixture.fanbox.close()
    }

    @Test
    fun downloadHttpFailureUsesPublicExceptionWithoutResponseFragment() = runBlocking {
        val fixture = createFixture { respond("download denied", HttpStatusCode.Forbidden) }
        val statement = fixture.fanbox.download("https://downloads.fanbox.cc/private.zip") {}

        val error = assertFailsWith<FanboxException.Forbidden> {
            statement.execute { it.bodyAsText() }
        }

        assertEquals("custom-request", error.endpoint)
        assertNull(error.rawBody)
        assertEquals(1, fixture.requests.size)
        fixture.fanbox.close()
    }

    @Test
    fun ownerClosePreventsDeferredDownloadFromReachingTransport() = runBlocking {
        val fixture = createFixture()
        val statement = fixture.fanbox.download("https://downloads.fanbox.cc/file.zip") {}

        fixture.fanbox.close()

        assertNotNull(runCatching { statement.execute { it.bodyAsText() } }.exceptionOrNull())
        assertTrue(fixture.requests.isEmpty())
        assertFailsWith<IllegalStateException> {
            fixture.fanbox.download("https://downloads.fanbox.cc/after-close.zip") {}
        }
        Unit
    }

    @Test
    fun pureValidatorClassifiesAllowedAndDisallowedHosts() {
        listOf(
            "https://fanbox.cc/file.zip",
            "https://downloads.fanbox.cc/file.zip",
            "https://pixiv.pximg.net/file.png",
            "https://fanbox.pixiv.net/file.gif",
        ).forEach { url ->
            assertEquals(Url(url), parseFanboxDownloadUrl(url))
        }

        val failure = assertIs<IllegalArgumentException>(
            runCatching { parseFanboxDownloadUrl("::invalid::") }.exceptionOrNull(),
        )
        assertEquals(IllegalArgumentException::class, failure::class)
    }

    private suspend fun createFixture(
        token: String? = "fixture-token",
        handler: MockRequestHandler = { respond("ok") },
    ): Fixture {
        val requests = mutableListOf<RecordedRequest>()
        val clients = mutableListOf<HttpClient>()
        val cookieStorage = AcceptAllCookiesStorage().also { storage ->
            storage.addCookie(
                requestUrl = Url("https://www.fanbox.cc"),
                cookie = Cookie(
                    name = "FANBOXSESSID",
                    value = "fixture-session",
                    domain = ".fanbox.cc",
                    path = "/",
                    secure = true,
                ),
            )
        }
        val clientFactory = FanboxHttpClientFactory { block ->
            HttpClient(
                MockEngine { request ->
                    requests += request.record()
                    handler(request)
                },
                block,
            ).also(clients::add)
        }
        val latestToken = MutableStateFlow(token)
        val dependencies = FanboxDependencies(
            cookieStorage = cookieStorage,
            cookies = emptyFlow(),
            csrfToken = latestToken,
            getCsrfToken = { latestToken.value },
            setCsrfToken = { latestToken.value = it },
            clearCsrfToken = { latestToken.value = null },
            clearCookies = {},
            overrideFanboxSessionId = {},
        )

        return Fixture(
            fanbox = Fanbox(
                dependencies = dependencies,
                clientFactory = clientFactory,
                ioDispatcher = Dispatchers.Default,
            ),
            requests = requests,
            clients = clients,
        )
    }

    private fun HttpRequestData.record(): RecordedRequest = RecordedRequest(
        url = url,
        csrfToken = headers[FANBOX_CSRF_HEADER],
        cookie = headers[HttpHeaders.Cookie],
    )

    private data class Fixture(
        val fanbox: Fanbox,
        val requests: List<RecordedRequest>,
        val clients: List<HttpClient>,
    )

    private data class RecordedRequest(
        val url: Url,
        val csrfToken: String?,
        val cookie: String?,
    )
}
