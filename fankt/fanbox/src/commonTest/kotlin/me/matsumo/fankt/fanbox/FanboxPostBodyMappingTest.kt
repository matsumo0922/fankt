package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class FanboxPostBodyMappingTest {

    @Test
    fun unknownFallbackRunsThroughPublicPostDetailPath() = runBlocking {
        val fanbox = createFanbox(FanboxPostJsonFixtures.postInfoVideo)
        try {
            val actual = fanbox.getPostDetail(FanboxPostId("10000001"))

            assertEquals(
                FanboxPostDetail.Body.Unknown(
                    type = "video",
                    rawBodyJson = """{"videoId":"fixture-video-1","serviceProvider":"fixture-provider"}""",
                ),
                actual.body,
            )
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun malformedKnownBodyUsesSchemaMismatchThroughPublicPostDetailPath() = runBlocking {
        val fanbox = createFanbox(FanboxPostJsonFixtures.postInfoMalformedImage)
        try {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> {
                fanbox.getPostDetail(FanboxPostId("10000001"))
            }

            assertEquals(200, failure.statusCode)
            assertEquals("post.info", failure.endpoint)
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun nullKnownBodyUsesSchemaMismatchThroughPublicPostDetailPath() = runBlocking {
        val fanbox = createFanbox(FanboxPostJsonFixtures.postInfoNullArticle)
        try {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> {
                fanbox.getPostDetail(FanboxPostId("10000001"))
            }

            assertEquals(200, failure.statusCode)
            assertEquals("post.info", failure.endpoint)
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun articleEmbedsRunThroughPublicPostDetailPath() = runBlocking {
        val fanbox = createFanbox(FanboxPostJsonFixtures.postInfoArticleEmbeds)
        try {
            val actual = fanbox.getPostDetail(FanboxPostId("10000001"))
            val article = assertIs<FanboxPostDetail.Body.Article>(actual.body)
            val embeds = article.blocks.filterIsInstance<FanboxPostDetail.Body.Article.Block.Embed>()

            assertEquals(
                listOf(
                    "https://twitter.com/_/status/twitter-content",
                    "https://www.youtube.com/watch?v=youtube-video",
                    "https://vimeo.com/vimeo-content",
                    "https://soundcloud.com/soundcloud-content",
                    "https://docs.google.com/forms/d/e/google-forms-content/viewform?usp=sf_link",
                    "https://www.pixiv.net/fanbox/fanbox-content",
                ),
                embeds.map { it.url },
            )
            assertEquals(7, article.blocks.filterIsInstance<FanboxPostDetail.Body.Article.Block.Unknown>().size)
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun malformedKnownArticleBlockUsesSchemaMismatchThroughPublicPostDetailPath() = runBlocking {
        val fanbox = createFanbox(FanboxPostJsonFixtures.postInfoMalformedArticleBlock)
        try {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> {
                fanbox.getPostDetail(FanboxPostId("10000001"))
            }

            assertEquals(200, failure.statusCode)
            assertEquals("post.info", failure.endpoint)
        } finally {
            fanbox.close()
        }
    }

    private fun createFanbox(responseBody: String): Fanbox {
        val clientFactory = FanboxHttpClientFactory { block ->
            HttpClient(
                MockEngine {
                    respond(
                        content = responseBody,
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
            clearCookies = {},
            overrideFanboxSessionId = {},
        )
        return Fanbox(
            dependencies = dependencies,
            clientFactory = clientFactory,
            ioDispatcher = Dispatchers.Default,
        )
    }
}
