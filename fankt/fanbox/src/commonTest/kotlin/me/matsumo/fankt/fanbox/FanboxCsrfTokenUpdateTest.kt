package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.fixture.FanboxMetadataHtmlFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
        assertEquals(3, fixture.clients.size)
        initialClients.zip(fixture.clients).forEach { (before, after) -> assertSame(before, after) }

        nextToken = "second-token"
        fixture.fanbox.updateCsrfToken()
        fixture.fanbox.likePost(FanboxPostId("101"))

        assertEquals(listOf("first-token", "second-token"), fixture.postHeaders)
        assertEquals(3, fixture.clients.size)
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
        assertEquals(3, fixture.clients.size)
        fixture.fanbox.close()
    }

    @Test
    fun siblingFanboxInstancesShareExplicitlyProvidedTokenState() = runBlocking {
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
        var replaceCount = 0
        val fixture = createFixture(
            latestToken = latestToken,
            replaceCookies = { _ ->
                replaceCount += 1
                assertEquals(null, latestToken.value)
                error("atomic replacement failed")
            },
        )

        assertFailsWith<IllegalStateException> {
            fixture.fanbox.setCookies(listOf(record("replacement", "value")), reset = true)
        }

        assertEquals(1, replaceCount)
        assertEquals(null, latestToken.value)
        fixture.fanbox.close()
    }

    @Test
    fun failedAtomicCookieReplacementStillLeavesTokenCleared() = runBlocking {
        val latestToken = MutableStateFlow<String?>("old-token")
        val failure = IllegalStateException("atomic replacement failed")
        val fixture = createFixture(
            latestToken = latestToken,
            replaceCookies = { _ -> throw failure },
        )

        val actual = assertFailsWith<IllegalStateException> {
            fixture.fanbox.setCookies(listOf(record("replacement", "value")), reset = true)
        }

        assertSame(failure, actual)
        assertEquals(null, latestToken.value)
        fixture.fanbox.close()
    }

    @Test
    fun additiveSessionCookieClearsTokenBeforeReplacementFailure() = runBlocking {
        val latestToken = MutableStateFlow<String?>("old-token")
        val fixture = createFixture(
            latestToken = latestToken,
            addCookie = {
                assertEquals(null, latestToken.value)
                error("cookie write failed")
            },
        )

        assertFailsWith<IllegalStateException> {
            fixture.fanbox.setCookies(listOf(record("FANBOXSESSID", "new-session")))
        }

        assertEquals(null, latestToken.value)
        fixture.fanbox.close()
    }

    @Test
    fun unrelatedAdditiveCookiePreservesToken() = runBlocking {
        val latestToken = MutableStateFlow<String?>("current-token")
        val fixture = createFixture(latestToken)

        fixture.fanbox.setCookies(listOf(record("theme", "dark")))

        assertEquals("current-token", latestToken.value)
        fixture.fanbox.close()
    }

    @Test
    fun closeRejectsOwnedClientsAndPublicEntryPointsAndIsIdempotent() = runBlocking {
        val fixture = createFixture(MutableStateFlow(null))
        val clients = fixture.clients.toList()

        fixture.fanbox.close()
        fixture.fanbox.close()

        assertEquals(3, clients.size)
        clients.forEach { client ->
            val requestCountBefore = fixture.requestCount()
            val failure = runCatching { client.get("https://api.fanbox.cc/after-close") }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals(requestCountBefore, fixture.requestCount())
        }
        assertFailsWith<IllegalStateException> { fixture.fanbox.cookies }
        assertFailsWith<IllegalStateException> { fixture.fanbox.csrfToken }
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
            if (factoryCalls == 3) error("factory failure")
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

        assertEquals(3, factoryCalls)
        assertEquals(2, clients.size)
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
        overrideFanboxSessionId: suspend (String) -> Unit = {},
        addCookie: suspend (FanboxCookieRecord) -> Unit = {},
        replaceCookies: suspend (List<FanboxCookieRecord>) -> Unit = {},
    ): FanboxFixture {
        val clients = mutableListOf<HttpClient>()
        val postHeaders = mutableListOf<String?>()
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

                        else -> respond("", HttpStatusCode.OK)
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
            overrideFanboxSessionId = overrideFanboxSessionId,
            addCookie = addCookie,
            replaceCookies = replaceCookies,
        )

        return FanboxFixture(
            fanbox = Fanbox(
                dependencies = dependencies,
                clientFactory = clientFactory,
                ioDispatcher = Dispatchers.Default,
            ),
            clients = clients,
            postHeaders = postHeaders,
            requestCount = { requestCount },
        )
    }

    private fun createDependencies(
        latestToken: MutableStateFlow<String?>,
        cookieStorage: CookiesStorage = AcceptAllCookiesStorage(),
        getCsrfToken: suspend () -> String? = { latestToken.value },
        setCsrfToken: suspend (String) -> Unit = { latestToken.value = it },
        overrideFanboxSessionId: suspend (String) -> Unit = {},
        addCookie: suspend (FanboxCookieRecord) -> Unit = {},
        replaceCookies: suspend (List<FanboxCookieRecord>) -> Unit = {},
    ) = FanboxDependencies(
        cookieStorage = cookieStorage,
        cookies = emptyFlow(),
        csrfToken = latestToken,
        getCsrfToken = getCsrfToken,
        setCsrfToken = setCsrfToken,
        clearCsrfToken = { latestToken.value = null },
        overrideFanboxSessionId = overrideFanboxSessionId,
        addCookie = addCookie,
        replaceCookies = replaceCookies,
    )

    private fun record(name: String, value: String) = FanboxCookieRecord(
        domain = "fanbox.cc",
        path = "/",
        name = name,
        value = value,
        expiresAtEpochMilliseconds = null,
        secure = true,
        hostOnly = false,
    )

    private data class FanboxFixture(
        val fanbox: Fanbox,
        val clients: List<HttpClient>,
        val postHeaders: List<String?>,
        val requestCount: () -> Int,
    )
}
