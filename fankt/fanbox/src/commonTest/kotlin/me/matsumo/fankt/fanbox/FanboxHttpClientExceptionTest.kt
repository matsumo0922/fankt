package me.matsumo.fankt.fanbox

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun repositorySchemaMismatchUsesEntireExecutorRawBody() = runBlocking {
        val secret = "fixture-secret"
        val body = """{"csrfToken":"$secret","unexpected":true}"""
        val fanbox = publicFanbox { respondJson(HttpStatusCode.OK, body) }
        try {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> {
                fanbox.getPostDetail(FanboxPostId("1"))
            }

            assertEquals(200, failure.statusCode)
            assertEquals("post.info", failure.endpoint)
            assertTrue("[REDACTED]" in failure.rawBody.orEmpty())
            assertFalse(secret in failure.rawBody.orEmpty())
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun networkFailureThroughProductionExecutorUsesNetworkSubtype() = runBlocking {
        val cause = IOException("transport-secret-must-not-be-in-message")
        val fanbox = publicFanbox { throw cause }
        try {
            val error = assertFailsWith<FanboxException.Network> {
                fanbox.getPostDetail(FanboxPostId("1"))
            }

            assertEquals("post.info", error.endpoint)
            assertIs<IOException>(error.cause)
            assertEquals(cause.message, error.cause?.message)
            assertNull(error.statusCode)
            assertNull(error.rawBody)
            assertFalse("transport-secret" in error.message.orEmpty())
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun cancellationPreservesIdentityThroughProductionExecutor() = runBlocking {
        val cancellation = CancellationException("cancel-now")
        val fanbox = publicFanbox { throw cancellation }
        try {
            val error = try {
                fanbox.getPostDetail(FanboxPostId("1"))
                error("Expected request to fail")
            } catch (failure: Throwable) {
                failure
            }

            assertIs<CancellationException>(error)
            assertEquals(cancellation.message, error.message)
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun executorAndDownloadSamePathKeepDiagnosticSourcesIsolated() = runBlocking {
        val reflectedSecret = "reflected-query-secret-and-response-body"
        val fanbox = publicFanbox {
            if (it.url.parameters.contains("token")) {
                respond(reflectedSecret, HttpStatusCode.Forbidden)
            } else {
                respondJson(HttpStatusCode.Forbidden, """{"error":"bounded-executor-body"}""")
            }
        }
        try {
            val executor = assertFailsWith<FanboxException.Forbidden> {
                fanbox.getPostDetail(FanboxPostId("1"))
            }
            val download = assertFailsWith<FanboxException.Forbidden> {
                fanbox.download("https://api.fanbox.cc/post.info?token=query-secret") { }
            }

            assertEquals("post.info", executor.endpoint)
            assertTrue(executor.rawBody.orEmpty().isNotEmpty())
            assertEquals("download", download.endpoint)
            assertNull(download.rawBody)
            assertFalse("query-secret" in download.message.orEmpty())
            assertFalse(reflectedSecret in download.message.orEmpty())
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun loggingEnabledExecutorErrorSharesSanitizedFragmentWithException() = runBlocking {
        val credential = "executor-credential-secret"
        val tail = "full-executor-response-tail"
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
            val fanbox = publicFanbox(logLevel = FanboxLogLevel.ALL) {
                respondJson(
                    HttpStatusCode.Forbidden,
                    """{"csrfToken":"$credential","error":"${"x".repeat(3_000)}$tail"}""",
                )
            }
            try {
                val error = assertFailsWith<FanboxException.Forbidden> {
                    fanbox.getPostDetail(FanboxPostId("1"))
                }
                val responseLog = assertNotNull(logs.firstOrNull { it.startsWith("FANBOX response: ") })
                val loggedFragment = responseLog.removePrefix("FANBOX response: ")

                assertEquals(error.rawBody, loggedFragment)
                assertTrue(loggedFragment.length <= FanboxExceptionFactory.MAX_RAW_BODY_LENGTH)
                assertFalse(credential in loggedFragment)
                assertFalse(tail in loggedFragment)
            } finally {
                fanbox.close()
            }
        } finally {
            Napier.takeLogarithm(antilog)
        }
    }

    private suspend fun requestFailure(
        status: HttpStatusCode,
        headers: Headers = Headers.Empty,
    ): FanboxException {
        val fullBodyTail = "full-raw-html-tail"
        val fanbox = publicFanbox {
            respond(
                content = "<html>" + "x".repeat(3_000) + fullBodyTail + "</html>",
                status = status,
                headers = Headers.build {
                    appendAll(headers)
                    append(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                },
            )
        }
        return try {
            val error = assertFailsWith<FanboxException> {
                fanbox.getPostDetail(FanboxPostId("query-value"))
            }

            assertTrue(requireNotNull(error.rawBody).length <= FanboxExceptionFactory.MAX_RAW_BODY_LENGTH)
            assertFalse(fullBodyTail in error.rawBody.orEmpty())
            assertFalse(fullBodyTail in error.message.orEmpty())
            assertFalse("query-value" in error.message.orEmpty())
            error
        } finally {
            fanbox.close()
        }
    }

    private fun publicFanbox(
        logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
        handler: MockRequestHandleScope.(
            HttpRequestData,
        ) -> HttpResponseData,
    ): Fanbox {
        val clientFactory = FanboxHttpClientFactory { block -> HttpClient(MockEngine(handler), block) }
        return Fanbox(
            clientFactory = clientFactory,
            logLevel = logLevel,
            ioDispatcher = Dispatchers.Default,
        )
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        status: HttpStatusCode,
        body: String,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
