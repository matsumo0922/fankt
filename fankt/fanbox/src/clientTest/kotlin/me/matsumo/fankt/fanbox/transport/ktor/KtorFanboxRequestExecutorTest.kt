package me.matsumo.fankt.fanbox.transport.ktor

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.FANBOX_CSRF_HEADER
import me.matsumo.fankt.fanbox.buildFanboxExecutorHttpClient
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointIds
import me.matsumo.fankt.fanbox.endpoint.FanboxHttpMethod
import me.matsumo.fankt.fanbox.endpoint.FanboxQueryParameter
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.transport.InvalidRequestDescriptorException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * guest が組み立てた request を、host が認証情報を付ける前に検証することを確かめる。
 *
 * 配信された guest がどんな descriptor を返しても、信頼する FANBOX の endpoint 以外へ Cookie と CSRF token が送られないことを守る。
 */
class KtorFanboxRequestExecutorTest {

    // The rejections below would also pass an executor that refuses everything, so an accepted request is the control.
    @Test
    fun acceptedApiGetEncodesQueryOnceAndAttachesCredentials() = runBlocking {
        val cookieStorage = CountingCookiesStorage()
        var requestCount = 0
        val rawQueryValue = "post id/%2F&="
        val engine = MockEngine { request ->
            requestCount += 1
            assertEquals("https://api.fanbox.cc/post.info", "${request.url.protocol.name}://${request.url.host}${request.url.encodedPath}")
            assertEquals(rawQueryValue, request.url.parameters["postId"])
            assertContains(request.url.encodedQuery, "%252F")
            assertEquals("session", request.headers[HttpHeaders.Cookie]?.substringAfter('='))
            assertEquals("latest-token", request.headers[FANBOX_CSRF_HEADER])
            respond("raw-body", HttpStatusCode.OK)
        }
        val client = executorClient(cookieStorage, { "latest-token" }, engine)

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
            assertEquals("raw-body", response.bodyText)
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

    private fun executorClient(
        cookieStorage: CookiesStorage = CountingCookiesStorage(),
        tokenProvider: suspend () -> String? = { null },
        engine: MockEngine,
    ) = buildFanboxExecutorHttpClient(
        cookieStorage = cookieStorage,
        csrfTokenProvider = tokenProvider,
        engine = engine,
    )

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
