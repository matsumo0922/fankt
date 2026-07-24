package me.matsumo.fankt.fanbox.transport.ktor

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Source
import me.matsumo.fankt.fanbox.FANBOX_CSRF_HEADER
import me.matsumo.fankt.fanbox.FanboxDiagnosticSource
import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.FanboxLogLevel
import me.matsumo.fankt.fanbox.buildFanboxExecutorHttpClient
import me.matsumo.fankt.fanbox.buildHttpClient
import me.matsumo.fankt.fanbox.createFanboxJson
import me.matsumo.fankt.fanbox.datasource.createFanboxPostApi
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointIds
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.endpoint.FanboxHttpMethod
import me.matsumo.fankt.fanbox.endpoint.FanboxQueryParameter
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.endpoint.fanboxUniqueRequestRoutes
import me.matsumo.fankt.fanbox.response.FanboxDiagnostics
import me.matsumo.fankt.fanbox.transport.FanboxCredentialBehavior
import me.matsumo.fankt.fanbox.transport.InvalidRequestDescriptorException
import me.matsumo.fankt.fanbox.transport.TrustedFanboxEndpointPolicy
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class KtorFanboxRequestExecutorTest {

    @Test
    fun trustedPolicyExhaustivelyCoversEndpointInventory() {
        assertEquals(fanboxUniqueRequestRoutes, TrustedFanboxEndpointPolicy.entries.keys)
        assertEquals(28, TrustedFanboxEndpointPolicy.entries.size)

        val postEndpoints = setOf(
            FanboxEndpointIds.followCreate,
            FanboxEndpointIds.followDelete,
            FanboxEndpointIds.postAddComment,
            FanboxEndpointIds.postDeleteComment,
            FanboxEndpointIds.postLikeComment,
            FanboxEndpointIds.postLikePost,
        )
        TrustedFanboxEndpointPolicy.entries.forEach { (endpointId, policy) ->
            assertEquals(
                if (endpointId == FanboxEndpointIds.homepage) {
                    TrustedFanboxEndpointPolicy.HOMEPAGE_ORIGIN
                } else {
                    TrustedFanboxEndpointPolicy.API_ORIGIN
                },
                policy.origin,
            )
            assertEquals(
                if (endpointId in postEndpoints) FanboxHttpMethod.POST else FanboxHttpMethod.GET,
                policy.method,
            )
            assertEquals(
                FanboxCredentialBehavior.SHARED_FANBOX_AUTHENTICATION,
                policy.credentialBehavior,
            )
        }
    }

    @Test
    fun acceptedApiGetEncodesQueryOnceAndPreservesCredentialsAndDefaultHeaders() = runBlocking {
        val cookieStorage = CountingCookiesStorage()
        var tokenReads = 0
        var requestCount = 0
        val rawQueryValue = "post id/%2F&="
        val engine = MockEngine { request ->
            requestCount += 1
            assertEquals("https", request.url.protocol.name)
            assertEquals("api.fanbox.cc", request.url.host)
            assertEquals("/post.info", request.url.encodedPath)
            assertEquals(rawQueryValue, request.url.parameters["postId"])
            assertContains(request.url.encodedQuery, "%252F")
            assertEquals("session", request.headers[HttpHeaders.Cookie]?.substringAfter('='))
            assertEquals("latest-token", request.headers[FANBOX_CSRF_HEADER])
            assertEquals("https://www.fanbox.cc", request.headers["origin"])
            assertEquals("https://www.fanbox.cc/", request.headers[HttpHeaders.Referrer])
            assertContains(requireNotNull(request.headers[HttpHeaders.UserAgent]), "Chrome/124.0.0.0")
            respond(
                content = "raw-body",
                status = HttpStatusCode.OK,
                headers = headersOf("x-response", "value"),
            )
        }
        val client = executorClient(cookieStorage, {
            tokenReads += 1
            "latest-token"
        }, engine)
        try {
            val response = KtorFanboxRequestExecutor(client).execute(
                RequestDescriptor(
                    endpointId = FanboxEndpointIds.postInfo,
                    path = "post.info",
                    method = FanboxHttpMethod.GET,
                    query = listOf(FanboxQueryParameter("postId", rawQueryValue)),
                ),
            )

            assertEquals(1, requestCount)
            assertTrue(cookieStorage.reads > 0)
            assertEquals(1, tokenReads)
            assertEquals(200, response.statusCode)
            assertEquals(listOf("value"), response.headers["x-response"])
            assertEquals("raw-body", response.bodyText)
        } finally {
            client.close()
        }
    }

    @Test
    fun homepageUsesExactWwwRootGet() = runBlocking {
        var requestCount = 0
        val client = executorClient(
            engine = MockEngine { request ->
                requestCount += 1
                assertEquals(HttpMethod.Get, request.method)
                assertEquals("https://www.fanbox.cc/", request.url.toString())
                respond("homepage", HttpStatusCode.OK)
            },
        )
        try {
            val response = KtorFanboxRequestExecutor(client).execute(FanboxEndpoints.homepage())
            assertEquals(1, requestCount)
            assertEquals("homepage", response.bodyText)
        } finally {
            client.close()
        }
    }

    @Test
    fun invalidDescriptorsAreRejectedBeforeCredentialLookupOrTransport() = runBlocking {
        val cookieStorage = CountingCookiesStorage()
        var tokenReads = 0
        var requestCount = 0
        val client = executorClient(
            cookieStorage,
            {
                tokenReads += 1
                "secret"
            },
            MockEngine {
                requestCount += 1
                respond("unexpected", HttpStatusCode.OK)
            },
        )
        val invalidDescriptors = listOf(
            RequestDescriptor(FanboxEndpointId("unknown"), "post.info", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.POST),
            RequestDescriptor(FanboxEndpointIds.postInfo, "", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.homepage, "homepage", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.postInfo, "https://evil.example/steal", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.postInfo, "//evil.example/steal", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.postInfo, "/post.info", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.postInfo, "user@evil.example", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.postInfo, "post.info?secret=value", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.postInfo, "post.info#fragment", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.postInfo, "post.info/../steal", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.postInfo, "post.info/%2e%2e/steal", FanboxHttpMethod.GET),
            RequestDescriptor(FanboxEndpointIds.postInfo, "post.info\\..\\steal", FanboxHttpMethod.GET),
        )

        try {
            invalidDescriptors.forEach { descriptor ->
                assertFailsWith<InvalidRequestDescriptorException> {
                    KtorFanboxRequestExecutor(client).execute(descriptor)
                }
            }
            assertEquals(0, cookieStorage.reads)
            assertEquals(0, tokenReads)
            assertEquals(0, requestCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun postPreservesRawJsonBytesAndExplicitTokenWithoutReadingDefaultToken() = runBlocking {
        val jsonBody = """{"postId":"123"}"""
        var tokenReads = 0
        val client = executorClient(
            tokenProvider = {
                tokenReads += 1
                "store-token"
            },
            engine = MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("explicit-token", request.headers[FANBOX_CSRF_HEADER])
                val content = assertIs<OutgoingContent.ByteArrayContent>(request.body)
                assertEquals(ContentType.Application.Json, content.contentType)
                assertEquals(jsonBody, content.bytes().decodeToString())
                respond("", HttpStatusCode.OK)
            },
        )
        val executor = KtorFanboxRequestExecutor(
            client = client,
            additionalHeaders = { headersOf(FANBOX_CSRF_HEADER, "explicit-token") },
        )
        try {
            executor.execute(
                RequestDescriptor(
                    endpointId = FanboxEndpointIds.postLikePost,
                    path = "post.likePost",
                    method = FanboxHttpMethod.POST,
                    jsonBody = jsonBody,
                ),
            )
            assertEquals(0, tokenReads)
        } finally {
            client.close()
        }
    }

    @Test
    fun executorPostWireMatchesExistingRawKtorSetup() = runBlocking {
        val jsonBody = """{"postId":"wire"}"""
        var existingWire: WireRequest? = null
        var executorWire: WireRequest? = null
        val existingClient = buildHttpClient(
            formatter = createFanboxJson(),
            cookieStorage = CountingCookiesStorage(),
            source = FanboxDiagnosticSource.LibraryGenerated,
            csrfTokenProvider = { "wire-token" },
            isEnableContentNegotiation = false,
            engine = MockEngine { request ->
                val content = assertIs<OutgoingContent.ByteArrayContent>(request.body)
                existingWire = WireRequest(
                    method = request.method.value,
                    url = request.url.toString(),
                    origin = request.headers["origin"],
                    referer = request.headers[HttpHeaders.Referrer],
                    userAgent = request.headers[HttpHeaders.UserAgent],
                    cookie = request.headers[HttpHeaders.Cookie],
                    csrfToken = request.headers[FANBOX_CSRF_HEADER],
                    contentType = content.contentType.toString(),
                    body = content.bytes().toList(),
                )
                respond("", HttpStatusCode.OK)
            },
        )
        val executorClient = executorClient(
            cookieStorage = CountingCookiesStorage(),
            tokenProvider = { "wire-token" },
            engine = MockEngine { request ->
                val content = assertIs<OutgoingContent.ByteArrayContent>(request.body)
                executorWire = WireRequest(
                    method = request.method.value,
                    url = request.url.toString(),
                    origin = request.headers["origin"],
                    referer = request.headers[HttpHeaders.Referrer],
                    userAgent = request.headers[HttpHeaders.UserAgent],
                    cookie = request.headers[HttpHeaders.Cookie],
                    csrfToken = request.headers[FANBOX_CSRF_HEADER],
                    contentType = content.contentType.toString(),
                    body = content.bytes().toList(),
                )
                respond("", HttpStatusCode.OK)
            },
        )

        try {
            existingClient.post("https://api.fanbox.cc/post.likePost") {
                setBody(TextContent(jsonBody, ContentType.Application.Json))
            }
            KtorFanboxRequestExecutor(executorClient).execute(
                RequestDescriptor(
                    FanboxEndpointIds.postLikePost,
                    "post.likePost",
                    FanboxHttpMethod.POST,
                    jsonBody = jsonBody,
                ),
            )

            assertEquals(existingWire, executorWire)
        } finally {
            existingClient.close()
            executorClient.close()
        }
    }

    @Test
    fun existingExecutorReadsLatestTokenForEveryAcceptedRequest() = runBlocking {
        var token = "first"
        val observedTokens = mutableListOf<String?>()
        val client = executorClient(
            tokenProvider = { token },
            engine = MockEngine { request ->
                observedTokens += request.headers[FANBOX_CSRF_HEADER]
                respond("", HttpStatusCode.OK)
            },
        )
        val executor = KtorFanboxRequestExecutor(client)
        try {
            executor.execute(FanboxEndpoints.likePost(me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId("1")))
            token = "second"
            executor.execute(FanboxEndpoints.likePost(me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId("2")))
            assertEquals(listOf<String?>("first", "second"), observedTokens)
        } finally {
            client.close()
        }
    }

    @Test
    fun getFollowsSameOriginRedirectsForAllPreservedStatuses() = runBlocking {
        listOf(
            HttpStatusCode.MovedPermanently,
            HttpStatusCode.Found,
            HttpStatusCode.TemporaryRedirect,
            HttpStatusCode.PermanentRedirect,
        ).forEach { redirectStatus ->
            var requestCount = 0
            var tokenReads = 0
            val client = executorClient(
                tokenProvider = {
                    tokenReads += 1
                    "token"
                },
                engine = MockEngine { request ->
                    requestCount += 1
                    if (requestCount == 1) {
                        respond("redirect", redirectStatus, headersOf(HttpHeaders.Location, "/post.info?redirected=1"))
                    } else {
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("1", request.url.parameters["redirected"])
                        respond("done", HttpStatusCode.OK)
                    }
                },
            )
            try {
                val response = KtorFanboxRequestExecutor(client).execute(
                    RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                )
                assertEquals("done", response.bodyText, redirectStatus.toString())
                assertEquals(2, requestCount, redirectStatus.toString())
                assertEquals(2, tokenReads, redirectStatus.toString())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun postRedirectsAreRejectedBeforeSecondCredentialLookupOrSend() = runBlocking {
        listOf(
            HttpStatusCode.MovedPermanently,
            HttpStatusCode.Found,
            HttpStatusCode.TemporaryRedirect,
            HttpStatusCode.PermanentRedirect,
        ).forEach { redirectStatus ->
            var requestCount = 0
            var tokenReads = 0
            val client = executorClient(
                tokenProvider = {
                    tokenReads += 1
                    "token"
                },
                engine = MockEngine {
                    requestCount += 1
                    respond("redirect", redirectStatus, headersOf(HttpHeaders.Location, "/post.likePost"))
                },
            )
            try {
                assertFailsWith<InvalidRequestDescriptorException> {
                    KtorFanboxRequestExecutor(client).execute(
                        RequestDescriptor(
                            FanboxEndpointIds.postLikePost,
                            "post.likePost",
                            FanboxHttpMethod.POST,
                            jsonBody = "{}",
                        ),
                    )
                }
                assertEquals(1, requestCount, redirectStatus.toString())
                assertEquals(1, tokenReads, redirectStatus.toString())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun generatedAndExecutorPostRedirectsBothStopAfterFirstRequest() = runBlocking {
        listOf(
            HttpStatusCode.MovedPermanently,
            HttpStatusCode.Found,
            HttpStatusCode.TemporaryRedirect,
            HttpStatusCode.PermanentRedirect,
        ).forEach { redirectStatus ->
            var generatedRequestCount = 0
            var executorRequestCount = 0
            val generatedClient = buildHttpClient(
                formatter = createFanboxJson(),
                cookieStorage = CountingCookiesStorage(),
                source = FanboxDiagnosticSource.LibraryGenerated,
                csrfTokenProvider = { "token" },
                isEnableContentNegotiation = false,
                engine = MockEngine {
                    generatedRequestCount += 1
                    respond("redirect", redirectStatus, headersOf(HttpHeaders.Location, "/post.likePost"))
                },
            )
            val generatedApi = Ktorfit.Builder()
                .baseUrl("https://api.fanbox.cc/")
                .httpClient(generatedClient)
                .build()
                .createFanboxPostApi()
            val executorClient = executorClient(
                engine = MockEngine {
                    executorRequestCount += 1
                    respond("redirect", redirectStatus, headersOf(HttpHeaders.Location, "/post.likePost"))
                },
            )
            try {
                assertNotNull(
                    runCatching {
                        generatedApi.likePost(TextContent("{}", ContentType.Application.Json))
                    }.exceptionOrNull(),
                    redirectStatus.toString(),
                )
                assertFailsWith<InvalidRequestDescriptorException> {
                    KtorFanboxRequestExecutor(executorClient).execute(
                        RequestDescriptor(
                            FanboxEndpointIds.postLikePost,
                            "post.likePost",
                            FanboxHttpMethod.POST,
                            jsonBody = "{}",
                        ),
                    )
                }
                assertEquals(1, generatedRequestCount, redirectStatus.toString())
                assertEquals(1, executorRequestCount, redirectStatus.toString())
            } finally {
                generatedClient.close()
                executorClient.close()
            }
        }
    }

    @Test
    fun unsafeRedirectsAreRejectedBeforeRedirectedCredentialLookupOrSend() = runBlocking {
        val locations = listOf(
            "https://evil.example/steal",
            "http://api.fanbox.cc/post.info",
            "https://user:password@api.fanbox.cc/post.info",
            "https://[",
            "//evil.example/steal",
            "https://api.fanbox.cc/post.info#fragment",
            "https://api.fanbox.cc/post.info/%2e%2e/steal",
        )
        locations.forEach { location ->
            var requestCount = 0
            var tokenReads = 0
            val client = executorClient(
                tokenProvider = {
                    tokenReads += 1
                    "token"
                },
                engine = MockEngine {
                    requestCount += 1
                    respond("redirect", HttpStatusCode.Found, headersOf(HttpHeaders.Location, location))
                },
            )
            try {
                assertFailsWith<InvalidRequestDescriptorException>(location) {
                    KtorFanboxRequestExecutor(client).execute(
                        RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                    )
                }
                assertEquals(1, requestCount, location)
                assertEquals(1, tokenReads, location)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun redirectLoopIsBoundedBeforeAnotherCredentialLookup() = runBlocking {
        var requestCount = 0
        var tokenReads = 0
        val client = executorClient(
            tokenProvider = {
                tokenReads += 1
                "token"
            },
            engine = MockEngine {
                requestCount += 1
                respond("redirect", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/post.info"))
            },
        )
        try {
            assertFailsWith<InvalidRequestDescriptorException> {
                KtorFanboxRequestExecutor(client, maxRedirects = 2).execute(
                    RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                )
            }
            assertEquals(3, requestCount)
            assertEquals(3, tokenReads)
        } finally {
            client.close()
        }
    }

    @Test
    fun nonSuccessBodyReadFailureKeepsStatusTypedFailures() = runBlocking {
        val unauthorizedClient = executorClient(
            engine = MockEngine {
                respond(
                    content = FailingContentChannel(
                        content = "partial unauthorized body".encodeToByteArray(),
                        failure = IOException("body read failure"),
                    ),
                    status = HttpStatusCode.Unauthorized,
                )
            },
        )
        try {
            val error = assertFailsWith<FanboxException.Unauthorized> {
                KtorFanboxRequestExecutor(unauthorizedClient).execute(
                    RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                )
            }
            assertEquals(401, error.statusCode)
            assertNull(error.rawBody)
        } finally {
            unauthorizedClient.close()
        }

        val rateLimitedClient = executorClient(
            engine = MockEngine {
                respond(
                    content = FailingContentChannel(
                        content = "partial rate limit body".encodeToByteArray(),
                        failure = IOException("body read failure"),
                    ),
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.RetryAfter, "45"),
                )
            },
        )
        try {
            val error = assertFailsWith<FanboxException.RateLimited> {
                KtorFanboxRequestExecutor(rateLimitedClient).execute(
                    RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                )
            }
            assertEquals(429, error.statusCode)
            assertEquals(45.seconds, error.retryAfter)
            assertNull(error.rawBody)
        } finally {
            rateLimitedClient.close()
        }
    }

    @Test
    fun readableNonSuccessBodyKeepsSanitizedBoundedDiagnostic() = runBlocking {
        val secret = "response-secret"
        val omittedTail = "must-not-survive-bounding"
        val body = """{"csrfToken":"$secret"}""" +
            "x".repeat(FanboxDiagnostics.MAX_FRAGMENT_LENGTH) +
            omittedTail
        val client = executorClient(
            engine = MockEngine { respond(body, HttpStatusCode.Forbidden) },
        )
        try {
            val error = assertFailsWith<FanboxException.Forbidden> {
                KtorFanboxRequestExecutor(client).execute(
                    RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                )
            }
            val rawBody = assertNotNull(error.rawBody)
            assertEquals(FanboxDiagnostics.MAX_FRAGMENT_LENGTH, rawBody.length)
            assertContains(rawBody, "[REDACTED]")
            assertFalse(secret in rawBody)
            assertFalse(omittedTail in rawBody)
        } finally {
            client.close()
        }
    }

    @Test
    fun redirectsAreValidatedWithoutReadingResponseBody() = runBlocking {
        val crossOriginBody = FailingContentChannel(failure = IOException("redirect body was read"))
        val crossOriginClient = executorClient(
            engine = MockEngine {
                respond(
                    content = crossOriginBody,
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://evil.example/steal"),
                )
            },
        )
        try {
            assertFailsWith<InvalidRequestDescriptorException> {
                KtorFanboxRequestExecutor(crossOriginClient).execute(
                    RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                )
            }
            assertEquals(0, crossOriginBody.readAttempts)
        } finally {
            crossOriginClient.close()
        }

        val methodChangingBody = FailingContentChannel(failure = IOException("redirect body was read"))
        val methodChangingClient = executorClient(
            engine = MockEngine {
                respond(
                    content = methodChangingBody,
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "/post.likePost"),
                )
            },
        )
        try {
            assertFailsWith<InvalidRequestDescriptorException> {
                KtorFanboxRequestExecutor(methodChangingClient).execute(
                    RequestDescriptor(
                        FanboxEndpointIds.postLikePost,
                        "post.likePost",
                        FanboxHttpMethod.POST,
                        jsonBody = "{}",
                    ),
                )
            }
            assertEquals(0, methodChangingBody.readAttempts)
        } finally {
            methodChangingClient.close()
        }
    }

    @Test
    fun cancellationDuringDiagnosticBodyReadPropagates() = runBlocking {
        val cancellation = CancellationException("body read cancelled")
        val client = executorClient(
            engine = MockEngine {
                respond(
                    content = FailingContentChannel(
                        content = "partial body".encodeToByteArray(),
                        failure = cancellation,
                    ),
                    status = HttpStatusCode.InternalServerError,
                )
            },
        )
        try {
            val error = assertFailsWith<CancellationException> {
                KtorFanboxRequestExecutor(client).execute(
                    RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                )
            }
            assertEquals(cancellation.message, error.message)
        } finally {
            client.close()
        }
    }

    @Test
    fun nonSuccessNetworkAndCancellationUseExistingFailureSemantics() = runBlocking {
        val rateLimitedClient = executorClient(
            engine = MockEngine {
                respond(
                    content = "rate-secret",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.RetryAfter, "30"),
                )
            },
        )
        try {
            val error = assertFailsWith<FanboxException.RateLimited> {
                KtorFanboxRequestExecutor(rateLimitedClient, logLevel = FanboxLogLevel.NONE).execute(
                    RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                )
            }
            assertEquals("post.info", error.endpoint)
            assertEquals(30.seconds, error.retryAfter)
        } finally {
            rateLimitedClient.close()
        }

        val networkCause = IOException("network failure")
        val networkClient = executorClient(engine = MockEngine { throw networkCause })
        try {
            val error = assertFailsWith<FanboxException.Network> {
                KtorFanboxRequestExecutor(networkClient).execute(
                    RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                )
            }
            val cause = assertIs<IOException>(error.cause)
            assertEquals(networkCause.message, cause.message)
            assertEquals("post.info", error.endpoint)
        } finally {
            networkClient.close()
        }

        val cancellation = CancellationException("cancel")
        val cancellationClient = executorClient(engine = MockEngine { throw cancellation })
        try {
            val error = assertFailsWith<CancellationException> {
                KtorFanboxRequestExecutor(cancellationClient).execute(
                    RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET),
                )
            }
            assertEquals(cancellation.message, error.message)
        } finally {
            cancellationClient.close()
        }
    }

    @Test
    fun closedExecutorClientStartsNoNewTransportWork() = runBlocking {
        var requestCount = 0
        val client = executorClient(
            engine = MockEngine {
                requestCount += 1
                respond("ok", HttpStatusCode.OK)
            },
        )
        val executor = KtorFanboxRequestExecutor(client)
        client.close()

        val failure = runCatching {
            executor.execute(RequestDescriptor(FanboxEndpointIds.postInfo, "post.info", FanboxHttpMethod.GET))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(0, requestCount)
    }

    private fun executorClient(
        cookieStorage: CookiesStorage = CountingCookiesStorage(),
        tokenProvider: suspend () -> String? = { null },
        engine: MockEngine,
    ) = buildFanboxExecutorHttpClient(
        cookieStorage = cookieStorage,
        csrfTokenProvider = tokenProvider,
        engine = engine,
    )

    private data class WireRequest(
        val method: String,
        val url: String,
        val origin: String?,
        val referer: String?,
        val userAgent: String?,
        val cookie: String?,
        val csrfToken: String?,
        val contentType: String,
        val body: List<Byte>,
    )

    @OptIn(InternalAPI::class)
    private class FailingContentChannel(
        content: ByteArray = ByteArray(0),
        private val failure: Throwable,
    ) : ByteReadChannel {
        private val buffer = Buffer().apply { write(content) }
        private var failed = false

        var readAttempts: Int = 0
            private set

        override val closedCause: Throwable?
            get() = failure.takeIf { failed }

        override val isClosedForRead: Boolean
            get() = failed

        override val readBuffer: Source
            get() = buffer

        override suspend fun awaitContent(min: Int): Boolean {
            if (buffer.size >= min) return true
            readAttempts += 1
            failed = true
            throw failure
        }

        override fun cancel(cause: Throwable?) = Unit
    }

    private class CountingCookiesStorage : CookiesStorage {
        var reads: Int = 0
            private set

        override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = Unit

        override suspend fun get(requestUrl: Url): List<Cookie> {
            reads += 1
            return listOf(Cookie(name = "FANBOXSESSID", value = "session"))
        }

        override fun close() = Unit
    }
}
