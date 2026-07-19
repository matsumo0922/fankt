package me.matsumo.fankt.fanbox

import de.jensklingenberg.ktorfit.Ktorfit
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.datasource.createFanboxPostApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import io.github.aakira.napier.LogLevel as NapierLogLevel

class FanboxHttpClientExceptionTest {

    @Test
    fun unauthorizedResponseUsesPublicSubtype() = runBlocking {
        val error = requestFailure(HttpStatusCode.Unauthorized)

        assertIs<FanboxException.Unauthorized>(error)
        assertEquals(401, error.statusCode)
        assertEquals("post.info", error.endpoint)
    }

    @Test
    fun forbiddenResponseUsesPublicSubtype() = runBlocking {
        assertIs<FanboxException.Forbidden>(requestFailure(HttpStatusCode.Forbidden))
        Unit
    }

    @Test
    fun notFoundResponseUsesPublicSubtype() = runBlocking {
        assertIs<FanboxException.NotFound>(requestFailure(HttpStatusCode.NotFound))
        Unit
    }

    @Test
    fun rateLimitedResponseUsesPublicSubtypeAndRetryAfter() = runBlocking {
        val error = requestFailure(
            status = HttpStatusCode.TooManyRequests,
            headers = headersOf(HttpHeaders.RetryAfter, "30"),
        )

        val rateLimited = assertIs<FanboxException.RateLimited>(error)
        assertEquals(30.seconds, rateLimited.retryAfter)
    }

    @Test
    fun serverResponseUsesPublicSubtypeAndActualStatus() = runBlocking {
        val error = requestFailure(HttpStatusCode.BadGateway)

        assertIs<FanboxException.ServerError>(error)
        assertEquals(502, error.statusCode)
    }

    @Test
    fun otherHttpErrorUsesUnexpectedSubtype() = runBlocking {
        val error = requestFailure(HttpStatusCode(418, "I'm a teapot"))

        assertIs<FanboxException.UnexpectedHttpError>(error)
        assertEquals(418, error.statusCode)
    }

    @Test
    fun successfulJsonThroughProductionConfigStillDecodes() = runBlocking {
        val client = mockClient(
            source = FanboxDiagnosticSource.LibraryGenerated,
            status = HttpStatusCode.OK,
            body = "{\"ok\":true}",
        )

        val body = client.get("https://api.fanbox.cc/post.info").body<ExpectedBody>()

        assertTrue(body.ok)
    }

    @Test
    fun successfulStringThroughProductionConfigUsesGeneratedClientSource() = runBlocking {
        val client = mockClient(
            source = FanboxDiagnosticSource.LibraryGenerated,
            status = HttpStatusCode.OK,
            body = "generated-response",
        )

        val body = client.get("https://api.fanbox.cc/post.info").body<String>()

        assertEquals("generated-response", body)
    }

    @Test
    fun successfulStringThroughProductionConfigUsesDownloadClientSource() = runBlocking {
        val client = mockClient(
            source = FanboxDiagnosticSource.Download,
            status = HttpStatusCode.OK,
            body = "raw-response",
        )

        val body = client.get("https://api.fanbox.cc/post.info").body<String>()

        assertEquals("raw-response", body)
    }

    @Test
    fun malformedJsonThroughProductionConfigUsesSchemaMismatch() = runBlocking {
        val tail = "full-html-tail-must-not-appear"
        val body = "{\"ok\":" + "x".repeat(3_000) + tail
        val client = mockClient(
            source = FanboxDiagnosticSource.LibraryGenerated,
            status = HttpStatusCode.OK,
            body = body,
        )

        val error = captureFailure { client.get("https://api.fanbox.cc/post.info").body<ExpectedBody>() }
        val mismatch = assertIs<FanboxException.SchemaMismatch>(error)

        assertEquals(200, mismatch.statusCode)
        assertEquals("post.info", mismatch.endpoint)
        assertTrue(requireNotNull(mismatch.rawBody).length <= FanboxExceptionFactory.MAX_RAW_BODY_LENGTH)
        assertFalse(tail in mismatch.rawBody.orEmpty())
        assertFalse(tail in mismatch.message.orEmpty())
    }

    @Test
    fun htmlContentTypeThroughProductionConfigUsesSchemaMismatch() = runBlocking {
        val client = mockClient(
            source = FanboxDiagnosticSource.LibraryGenerated,
            status = HttpStatusCode.OK,
            body = "<html>not-json</html>",
            contentType = ContentType.Text.Html,
        )

        val error = captureFailure { client.get("https://api.fanbox.cc/post.info").body<ExpectedBody>() }
        val mismatch = assertIs<FanboxException.SchemaMismatch>(error)

        assertEquals(200, mismatch.statusCode)
        assertEquals("post.info", mismatch.endpoint)
        assertEquals("<html>not-json</html>", mismatch.rawBody)
    }

    @Test
    fun networkFailureThroughProductionConfigUsesNetworkSubtype() = runBlocking {
        val cause = IOException("transport-secret-must-not-be-in-message")
        val client = HttpClient(MockEngine { throw cause }) {
            configureFanboxClient(createFanboxJson(), FanboxDiagnosticSource.LibraryGenerated)
        }

        val error = captureFailure { client.get("https://api.fanbox.cc/post.info") }
        val network = assertIs<FanboxException.Network>(error)

        val networkCause = assertIs<IOException>(network.cause)
        assertEquals(cause.message, networkCause.message)
        assertNull(network.statusCode)
        assertNull(network.rawBody)
        assertFalse("transport-secret" in network.message.orEmpty())
    }

    @Test
    fun cancellationPreservesIdentity() = runBlocking {
        val cancellation = CancellationException("cancel-now")
        val client = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) {
            configureFanboxClient(createFanboxJson(), FanboxDiagnosticSource.LibraryGenerated)
            HttpResponseValidator {
                validateResponse { throw cancellation }
            }
        }

        val error = captureFailure { client.get("https://api.fanbox.cc/post.info") }

        assertSame(cancellation, error)
    }

    @Test
    fun generatedAndDownloadSamePathKeepDiagnosticSourcesIsolated() = runBlocking {
        val reflectedSecret = "reflected-query-secret-and-response-body"
        val generatedClient = mockClient(
            source = FanboxDiagnosticSource.LibraryGenerated,
            status = HttpStatusCode.Forbidden,
            body = "{\"error\":\"bounded-generated-body\"}",
        )
        val generatedApi = Ktorfit.Builder()
            .baseUrl("https://api.fanbox.cc/")
            .httpClient(generatedClient)
            .build()
            .createFanboxPostApi()
        val downloadClient = mockClient(
            source = FanboxDiagnosticSource.Download,
            status = HttpStatusCode.Forbidden,
            body = reflectedSecret,
            logLevel = FanboxLogLevel.ALL,
        )

        val generated = assertIs<FanboxException.Forbidden>(
            captureFailure { generatedApi.getPostDetail("1") },
        )
        val raw = assertIs<FanboxException.Forbidden>(
            captureFailure {
                downloadClient.get("https://api.fanbox.cc/post.info?token=query-secret").bodyAsText()
            },
        )

        assertEquals("post.info", generated.endpoint)
        assertTrue(generated.rawBody.orEmpty().isNotEmpty())
        assertEquals("download", raw.endpoint)
        assertNull(raw.rawBody)
        assertFalse("query-secret" in raw.message.orEmpty())
        assertFalse(reflectedSecret in raw.message.orEmpty())
    }

    @Test
    fun loggingEnabledGeneratedErrorSharesSanitizedFragmentWithException() = runBlocking {
        val credential = "generated-credential-secret"
        val tail = "full-generated-response-tail"
        val logs = mutableListOf<String>()
        val antilog = object : Antilog() {
            override fun performLog(
                priority: NapierLogLevel,
                tag: String?,
                throwable: Throwable?,
                message: String?,
            ) {
                message?.let(logs::add)
            }
        }
        Napier.base(antilog)

        try {
            val client = mockClient(
                source = FanboxDiagnosticSource.LibraryGenerated,
                status = HttpStatusCode.Forbidden,
                body = "{\"csrfToken\":\"$credential\",\"error\":\"${"x".repeat(3_000)}$tail\"}",
                logLevel = FanboxLogLevel.ALL,
            )
            val error = assertIs<FanboxException.Forbidden>(
                captureFailure { client.get("https://api.fanbox.cc/post.info").bodyAsText() },
            )
            val responseLog = assertNotNull(logs.firstOrNull { it.startsWith("FANBOX response: ") })
            val loggedFragment = responseLog.removePrefix("FANBOX response: ")

            assertEquals(error.rawBody, loggedFragment)
            assertTrue(loggedFragment.length <= FanboxExceptionFactory.MAX_RAW_BODY_LENGTH)
            assertFalse(credential in loggedFragment)
            assertFalse(tail in loggedFragment)
            assertFalse(credential in error.rawBody.orEmpty())
            assertFalse(tail in error.rawBody.orEmpty())
        } finally {
            Napier.takeLogarithm(antilog)
        }
    }

    private suspend fun requestFailure(
        status: HttpStatusCode,
        headers: Headers = Headers.Empty,
    ): FanboxException {
        val fullBodyTail = "full-raw-html-tail"
        val client = mockClient(
            source = FanboxDiagnosticSource.LibraryGenerated,
            status = status,
            body = "<html>" + "x".repeat(3_000) + fullBodyTail + "</html>",
            headers = headers,
            contentType = ContentType.Text.Html,
        )
        val error = captureFailure {
            client.get("https://api.fanbox.cc/post.info?secret=query-value").bodyAsText()
        }
        val fanbox = assertIs<FanboxException>(error)

        assertTrue(requireNotNull(fanbox.rawBody).length <= FanboxExceptionFactory.MAX_RAW_BODY_LENGTH)
        assertFalse(fullBodyTail in fanbox.rawBody.orEmpty())
        assertFalse(fullBodyTail in fanbox.message.orEmpty())
        assertFalse("query-value" in fanbox.message.orEmpty())
        return fanbox
    }

    private fun mockClient(
        source: FanboxDiagnosticSource,
        status: HttpStatusCode,
        body: String,
        headers: Headers = Headers.Empty,
        contentType: ContentType = ContentType.Application.Json,
        logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
    ): HttpClient {
        val responseHeaders = Headers.build {
            appendAll(headers)
            if (!contains(HttpHeaders.ContentType)) {
                append(HttpHeaders.ContentType, contentType.toString())
            }
        }
        return HttpClient(MockEngine { respond(body, status, responseHeaders) }) {
            configureFanboxClient(createFanboxJson(), source, logLevel)
        }
    }

    private suspend fun captureFailure(block: suspend () -> Any?): Throwable = try {
        block()
        error("Expected request to fail")
    } catch (error: Throwable) {
        error
    }

    @Serializable
    private data class ExpectedBody(val ok: Boolean)
}
