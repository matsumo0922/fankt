package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FanboxCommentSubmissionTest {

    @Test
    fun rootCommentOmitsParentIds() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.fanbox.addComment(
                postId = FanboxPostId("post-id"),
                rootCommentId = null,
                parentCommentId = null,
                body = "root comment",
            )

            val body = fixture.requestBodies.single().let(Json::parseToJsonElement).jsonObject
            assertEquals("post-id", body.getValue("postId").jsonPrimitive.content)
            assertEquals("root comment", body.getValue("body").jsonPrimitive.content)
            assertFalse("rootCommentId" in body)
            assertFalse("parentCommentId" in body)
        } finally {
            fixture.fanbox.close()
        }
    }

    @Test
    fun replyCommentIncludesParentIds() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.fanbox.addComment(
                postId = FanboxPostId("post-id"),
                rootCommentId = FanboxCommentId("root-id"),
                parentCommentId = FanboxCommentId("parent-id"),
                body = "reply comment",
            )

            val body = fixture.requestBodies.single().let(Json::parseToJsonElement).jsonObject
            assertEquals("root-id", body.getValue("rootCommentId").jsonPrimitive.content)
            assertEquals("parent-id", body.getValue("parentCommentId").jsonPrimitive.content)
        } finally {
            fixture.fanbox.close()
        }
    }

    private fun createFixture(): Fixture {
        val requestBodies = mutableListOf<String>()
        val clientFactory = FanboxHttpClientFactory { block ->
            HttpClient(
                MockEngine { request ->
                    val body = request.body as OutgoingContent.ByteArrayContent
                    requestBodies += body.bytes().decodeToString()
                    respond(content = "", status = HttpStatusCode.OK)
                },
                block,
            )
        }
        val csrfToken = MutableStateFlow<String?>("csrf-token")
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
            requestBodies = requestBodies,
        )
    }

    private data class Fixture(
        val fanbox: Fanbox,
        val requestBodies: List<String>,
    )
}
