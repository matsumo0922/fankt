package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxCreatorPostsPaginationTest {

    @Test
    fun emptyPaginationReturnsEmptyPageWithoutRequestingCreatorPosts() = runBlocking {
        val fixture = createFixture(FanboxPostJsonFixtures.paginateCreatorEmpty)
        try {
            val page = fixture.fanbox.getCreatorPosts(CREATOR_ID, cursor = null, nextCursor = null)

            assertEquals(emptyList(), page.contents)
            assertEquals(null, page.cursor)
            assertEquals(
                listOf("post.paginateCreator"),
                fixture.requestUrls.map(Url::encodedPath).map { it.substringAfterLast('/') },
            )
        } finally {
            fixture.fanbox.close()
        }
    }

    @Test
    fun initialRequestUsesLimitFromPaginationCursor() = runBlocking {
        val fixture = createFixture(FanboxPostJsonFixtures.paginateCreatorNormal)
        try {
            fixture.fanbox.getCreatorPosts(CREATOR_ID, cursor = null, nextCursor = null)

            assertEquals("10", fixture.creatorPostsRequest().parameters["limit"])
        } finally {
            fixture.fanbox.close()
        }
    }

    @Test
    fun continuationRequestUsesLimitFromCallerCursor() = runBlocking {
        val fixture = createFixture(FanboxPostJsonFixtures.paginateCreatorEmpty)
        try {
            fixture.fanbox.getCreatorPosts(CREATOR_ID, cursor = cursor(limit = 15), nextCursor = null)

            assertEquals("15", fixture.creatorPostsRequest().parameters["limit"])
            assertEquals(0, fixture.requestUrls.count { it.encodedPath.endsWith("post.paginateCreator") })
        } finally {
            fixture.fanbox.close()
        }
    }

    @Test
    fun missingCursorLimitFallsBackToTwenty() = runBlocking {
        val fixture = createFixture(FanboxPostJsonFixtures.paginateCreatorEmpty)
        try {
            fixture.fanbox.getCreatorPosts(CREATOR_ID, cursor = cursor(limit = null), nextCursor = null)

            assertEquals("20", fixture.creatorPostsRequest().parameters["limit"])
        } finally {
            fixture.fanbox.close()
        }
    }

    private fun createFixture(paginationBody: String): Fixture {
        val requestUrls = mutableListOf<Url>()
        val clientFactory = FanboxHttpClientFactory { block ->
            HttpClient(
                MockEngine { request ->
                    requestUrls += request.url
                    val body = when {
                        request.url.encodedPath.endsWith("post.paginateCreator") -> paginationBody
                        request.url.encodedPath.endsWith("post.listCreator") -> FanboxPostJsonFixtures.postListCreatorEmpty
                        else -> error("Unexpected request: ${request.url}")
                    }
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
                block,
            )
        }
        val csrfToken = MutableStateFlow<String?>(null)
        val dependencies = FanboxDependencies(
            cookieStorage = AcceptAllCookiesStorage(),
            cookies = emptyFlow(),
            csrfToken = csrfToken,
            getCsrfToken = { csrfToken.value },
            setCsrfToken = { csrfToken.value = it },
            clearCsrfToken = { csrfToken.value = null },
            overrideFanboxSessionId = {},
            addCookie = {},
            replaceCookies = {},
        )
        return Fixture(
            fanbox = Fanbox(
                dependencies = dependencies,
                clientFactory = clientFactory,
                ioDispatcher = Dispatchers.Default,
            ),
            requestUrls = requestUrls,
        )
    }

    private fun Fixture.creatorPostsRequest(): Url = requestUrls.single {
        it.encodedPath.endsWith("post.listCreator")
    }

    private fun cursor(limit: Int?) = FanboxCursor(
        firstPublishedDatetime = "2000-01-01T00:00:00+00:00",
        maxPublishedDatetime = null,
        firstId = "10000001",
        maxId = null,
        limit = limit,
    )

    private data class Fixture(
        val fanbox: Fanbox,
        val requestUrls: List<Url>,
    )

    private companion object {
        val CREATOR_ID = FanboxCreatorId("fixture-creator-1")
    }
}
