package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.fixture.FanboxMetadataHtmlFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class FanboxCsrfTokenUpdateTest {

    @Test
    fun metadataFailurePropagatesWithoutStoringToken() = runBlocking {
        val failure = FanboxException.SchemaMismatch(
            statusCode = 200,
            rawBody = "bounded",
            endpoint = "homepage",
            cause = null,
        )
        var insertCount = 0

        val actual = try {
            fetchAndStoreCsrfToken(
                fetchMetadata = { throw failure },
                storeToken = { insertCount += 1 },
            )
            error("Expected metadata fetch to fail")
        } catch (error: FanboxException.SchemaMismatch) {
            error
        }

        assertSame(failure, actual)
        assertEquals(0, insertCount)
    }

    @Test
    fun completedUpdateIsUsedByNextPostWithoutRebuildingInternalClients() = runBlocking {
        val latestToken = MutableStateFlow<String?>(null)
        var nextToken = "first-token"
        val fixture = createFixture(
            latestToken = latestToken,
            metadataToken = { nextToken },
            setCsrfToken = { token -> latestToken.value = token },
        )
        val initialClients = fixture.clients.toList()

        fixture.fanbox.updateCsrfToken()
        assertEquals("first-token", fixture.fanbox.csrfToken.first())
        fixture.fanbox.likePost(FanboxPostId("100"))

        assertEquals("first-token", fixture.postHeaders.last())
        assertEquals(5, fixture.clients.size)
        initialClients.zip(fixture.clients).forEach { (before, after) -> assertSame(before, after) }

        nextToken = "second-token"
        fixture.fanbox.updateCsrfToken()
        fixture.fanbox.likePost(FanboxPostId("101"))

        assertEquals(listOf("first-token", "second-token"), fixture.postHeaders)
        assertEquals(5, fixture.clients.size)
        initialClients.zip(fixture.clients).forEach { (before, after) -> assertSame(before, after) }
        fixture.fanbox.close()
    }

    @Test
    fun concurrentUpdatesUseLastCompletedStore() = runBlocking {
        val latestToken = MutableStateFlow<String?>(null)
        val firstStoreStarted = CompletableDeferred<Unit>()
        val allowFirstStore = CompletableDeferred<Unit>()
        var metadataRequestCount = 0
        val fixture = createFixture(
            latestToken = latestToken,
            metadataToken = {
                metadataRequestCount += 1
                if (metadataRequestCount == 1) "first-token" else "second-token"
            },
            setCsrfToken = { token ->
                if (token == "first-token") {
                    firstStoreStarted.complete(Unit)
                    allowFirstStore.await()
                }
                latestToken.value = token
            },
        )

        val firstUpdate = async { fixture.fanbox.updateCsrfToken() }
        firstStoreStarted.await()
        val secondUpdate = async { fixture.fanbox.updateCsrfToken() }
        secondUpdate.await()
        allowFirstStore.complete(Unit)
        firstUpdate.await()
        fixture.fanbox.likePost(FanboxPostId("102"))

        assertEquals("first-token", fixture.postHeaders.last())
        assertEquals(5, fixture.clients.size)
        fixture.fanbox.close()
    }

    @Test
    fun explicitRawClientHeaderKeepsCallerValueWithoutTokenLookup() = runBlocking {
        val latestToken = MutableStateFlow<String?>("memory-token")
        var lookupCount = 0
        val fixture = createFixture(
            latestToken = latestToken,
            getCsrfToken = {
                lookupCount += 1
                latestToken.value
            },
        )
        val rawClient = fixture.fanbox.getHttpClient()

        rawClient.get("https://api.fanbox.cc/custom") {
            header("x-csrf-token", "explicit-token")
        }.bodyAsText()

        assertEquals("explicit-token", fixture.rawHeaders.last())
        assertEquals(0, lookupCount)
        fixture.fanbox.close()
    }

    @Test
    fun siblingFanboxInstancesShareProcessTokenState() = runBlocking {
        val sharedToken = MutableStateFlow<String?>(null)
        val firstFixture = createFixture(sharedToken, metadataToken = { "shared-token" })
        val secondFixture = createFixture(sharedToken)

        firstFixture.fanbox.updateCsrfToken()
        firstFixture.fanbox.close()
        secondFixture.fanbox.likePost(FanboxPostId("shared"))

        assertEquals("shared-token", secondFixture.fanbox.csrfToken.first())
        assertEquals("shared-token", secondFixture.postHeaders.last())
        secondFixture.fanbox.close()
    }

    @Test
    fun successfulSessionReplacementClearsToken() = runBlocking {
        val latestToken = MutableStateFlow<String?>("old-token")
        val fixture = createFixture(latestToken)

        fixture.fanbox.setFanboxSessionId("new-session")

        assertEquals(null, latestToken.value)
        fixture.fanbox.close()
    }

    @Test
    fun failedSessionReplacementPreservesToken() = runBlocking {
        val latestToken = MutableStateFlow<String?>("old-token")
        val failure = IllegalStateException("session write failed")
        val fixture = createFixture(
            latestToken = latestToken,
            overrideFanboxSessionId = { throw failure },
        )

        val actual = assertFailsWith<IllegalStateException> {
            fixture.fanbox.setFanboxSessionId("new-session")
        }

        assertSame(failure, actual)
        assertEquals("old-token", latestToken.value)
        fixture.fanbox.close()
    }

    @Test
    fun resetCookiesClearsTokenBeforeReplacementFailure() = runBlocking {
        val latestToken = MutableStateFlow<String?>("old-token")
        var clearCount = 0
        val fixture = createFixture(
            latestToken = latestToken,
            cookieStorage = failingCookieStorage { assertEquals(null, latestToken.value) },
            clearCookies = { clearCount += 1 },
        )

        assertFailsWith<IllegalStateException> {
            fixture.fanbox.setCookies(listOf(Cookie("replacement", "value")), reset = true)
        }

        assertEquals(1, clearCount)
        assertEquals(null, latestToken.value)
        fixture.fanbox.close()
    }

    @Test
    fun failedCookieResetPreservesToken() = runBlocking {
        val latestToken = MutableStateFlow<String?>("old-token")
        val failure = IllegalStateException("cookie clear failed")
        val fixture = createFixture(
            latestToken = latestToken,
            clearCookies = { throw failure },
        )

        val actual = assertFailsWith<IllegalStateException> {
            fixture.fanbox.setCookies(listOf(Cookie("replacement", "value")), reset = true)
        }

        assertSame(failure, actual)
        assertEquals("old-token", latestToken.value)
        fixture.fanbox.close()
    }

    @Test
    fun additiveSessionCookieClearsTokenBeforeReplacementFailure() = runBlocking {
        val latestToken = MutableStateFlow<String?>("old-token")
        val fixture = createFixture(
            latestToken = latestToken,
            cookieStorage = failingCookieStorage { assertEquals(null, latestToken.value) },
        )

        assertFailsWith<IllegalStateException> {
            fixture.fanbox.setCookies(listOf(Cookie("FANBOXSESSID", "new-session")))
        }

        assertEquals(null, latestToken.value)
        fixture.fanbox.close()
    }

    @Test
    fun unrelatedAdditiveCookiePreservesToken() = runBlocking {
        val latestToken = MutableStateFlow<String?>("current-token")
        val fixture = createFixture(latestToken)

        fixture.fanbox.setCookies(listOf(Cookie("theme", "dark")))

        assertEquals("current-token", latestToken.value)
        fixture.fanbox.close()
    }

    @Test
    fun rawClientsAreSharedPerConfigurationWithoutChangingConversionBehavior() = runBlocking {
        val fixture = createFixture(MutableStateFlow(null))
        val defaultClient = fixture.fanbox.getHttpClient()
        val sameDefaultClient = fixture.fanbox.getHttpClient(isEnableContentNegotiation = true)
        val plainClient = fixture.fanbox.getHttpClient(isEnableContentNegotiation = false)
        val samePlainClient = fixture.fanbox.getHttpClient(isEnableContentNegotiation = false)

        assertSame(defaultClient, sameDefaultClient)
        assertSame(plainClient, samePlainClient)
        assertNotSame(defaultClient, plainClient)
        assertEquals(5, fixture.clients.size)
        assertEquals(
            "ok",
            defaultClient.get("https://api.fanbox.cc/json").body<JsonObject>()["value"]?.jsonPrimitive?.content,
        )
        assertFailsWith<NoTransformationFoundException> {
            plainClient.get("https://api.fanbox.cc/json").body<JsonObject>()
        }
        fixture.fanbox.close()
    }

    @Test
    fun closeRejectsOwnedClientsAndPublicEntryPointsAndIsIdempotent() = runBlocking {
        val fixture = createFixture(MutableStateFlow(null))
        val clients = fixture.clients.toList()

        fixture.fanbox.close()
        fixture.fanbox.close()

        assertEquals(5, clients.size)
        clients.forEach { client ->
            val requestCountBefore = fixture.requestCount()
            val failure = runCatching { client.get("https://api.fanbox.cc/after-close") }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals(requestCountBefore, fixture.requestCount())
        }
        assertFailsWith<IllegalStateException> { fixture.fanbox.cookies }
        assertFailsWith<IllegalStateException> { fixture.fanbox.csrfToken }
        assertFailsWith<IllegalStateException> { fixture.fanbox.getHttpClient() }
        assertFailsWith<IllegalStateException> { fixture.fanbox.setFanboxSessionId("session") }
        assertFailsWith<IllegalStateException> { fixture.fanbox.setCookies(emptyList()) }
        assertFailsWith<IllegalStateException> { fixture.fanbox.updateCsrfToken() }
        assertFailsWith<IllegalStateException> { fixture.fanbox.likePost(FanboxPostId("after-close")) }
        Unit
    }

    @Test
    fun failedConstructionClosesEveryClientReturnedByTheFactory() = runBlocking {
        val clients = mutableListOf<HttpClient>()
        var factoryCalls = 0
        var requestCount = 0
        val factory = FanboxHttpClientFactory { block ->
            factoryCalls += 1
            if (factoryCalls == 4) error("factory failure")
            HttpClient(
                MockEngine {
                    requestCount += 1
                    respond("", HttpStatusCode.OK)
                },
                block,
            ).also(clients::add)
        }

        assertFailsWith<IllegalStateException> {
            Fanbox(
                dependencies = createDependencies(MutableStateFlow(null)),
                clientFactory = factory,
                ioDispatcher = Dispatchers.Default,
            )
        }

        assertEquals(4, factoryCalls)
        assertEquals(3, clients.size)
        clients.forEach { client ->
            val failure = runCatching { client.get("https://api.fanbox.cc/after-failure") }.exceptionOrNull()
            assertNotNull(failure)
        }
        assertEquals(0, requestCount)
    }

    private fun createFixture(
        latestToken: MutableStateFlow<String?>,
        metadataToken: () -> String = { "fixture-token" },
        getCsrfToken: suspend () -> String? = { latestToken.value },
        setCsrfToken: suspend (String) -> Unit = { latestToken.value = it },
        cookieStorage: CookiesStorage = AcceptAllCookiesStorage(),
        clearCookies: suspend () -> Unit = {},
        overrideFanboxSessionId: suspend (String) -> Unit = {},
    ): FanboxFixture {
        val clients = mutableListOf<HttpClient>()
        val postHeaders = mutableListOf<String?>()
        val rawHeaders = mutableListOf<String?>()
        var requestCount = 0
        val clientFactory = FanboxHttpClientFactory { block ->
            HttpClient(
                MockEngine { request ->
                    requestCount += 1
                    when {
                        request.url.host == "www.fanbox.cc" -> respond(
                            content = FanboxMetadataHtmlFixtures.home.replace("fixture-token", metadataToken()),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"),
                        )

                        request.url.encodedPath.endsWith("post.likePost") -> {
                            postHeaders += request.headers["x-csrf-token"]
                            respond("", HttpStatusCode.OK)
                        }

                        request.url.encodedPath.endsWith("json") -> respond(
                            content = """{"value":"ok"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        else -> {
                            rawHeaders += request.headers["x-csrf-token"]
                            respond("", HttpStatusCode.OK)
                        }
                    }
                },
                block,
            ).also(clients::add)
        }
        val dependencies = createDependencies(
            latestToken = latestToken,
            cookieStorage = cookieStorage,
            getCsrfToken = getCsrfToken,
            setCsrfToken = setCsrfToken,
            clearCookies = clearCookies,
            overrideFanboxSessionId = overrideFanboxSessionId,
        )

        return FanboxFixture(
            fanbox = Fanbox(
                dependencies = dependencies,
                clientFactory = clientFactory,
                ioDispatcher = Dispatchers.Default,
            ),
            clients = clients,
            postHeaders = postHeaders,
            rawHeaders = rawHeaders,
            requestCount = { requestCount },
        )
    }

    private fun createDependencies(
        latestToken: MutableStateFlow<String?>,
        cookieStorage: CookiesStorage = AcceptAllCookiesStorage(),
        getCsrfToken: suspend () -> String? = { latestToken.value },
        setCsrfToken: suspend (String) -> Unit = { latestToken.value = it },
        clearCookies: suspend () -> Unit = {},
        overrideFanboxSessionId: suspend (String) -> Unit = {},
    ) = FanboxDependencies(
        cookieStorage = cookieStorage,
        cookies = emptyFlow(),
        csrfToken = latestToken,
        getCsrfToken = getCsrfToken,
        setCsrfToken = setCsrfToken,
        clearCsrfToken = { latestToken.value = null },
        clearCookies = clearCookies,
        overrideFanboxSessionId = overrideFanboxSessionId,
    )

    private fun failingCookieStorage(onAdd: () -> Unit): CookiesStorage {
        return object : CookiesStorage {
            override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
                onAdd()
                error("cookie write failed")
            }

            override suspend fun get(requestUrl: Url): List<Cookie> = emptyList()

            override fun close() = Unit
        }
    }

    private data class FanboxFixture(
        val fanbox: Fanbox,
        val clients: List<HttpClient>,
        val postHeaders: List<String?>,
        val rawHeaders: List<String?>,
        val requestCount: () -> Int,
    )
}
