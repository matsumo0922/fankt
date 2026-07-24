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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxFollowCreatorSubmissionTest {

    @Test
    fun followCreatorSendsCreatorUserIdAsJsonString() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.fanbox.followCreator(FanboxUserId(1234567890123))

            val body = fixture.requestBodies.single().let(Json::parseToJsonElement).jsonObject
            assertEquals(JsonPrimitive("1234567890123"), body.getValue("creatorUserId"))
        } finally {
            fixture.fanbox.close()
        }
    }

    @Test
    fun unfollowCreatorSendsCreatorUserIdAsJsonString() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.fanbox.unfollowCreator(FanboxUserId(1234567890123))

            val body = fixture.requestBodies.single().let(Json::parseToJsonElement).jsonObject
            assertEquals(JsonPrimitive("1234567890123"), body.getValue("creatorUserId"))
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
