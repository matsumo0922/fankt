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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorDetailEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.fixture.FanboxCreatorJsonFixtures
import me.matsumo.fankt.fanbox.fixture.decodeFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class FanboxCreatorProfileItemTest {

    @Test
    fun actualDerivedMixedProfileItemsRunThroughPublicCreatorDetailPath() = runBlocking {
        val fanbox = createFanbox(FanboxCreatorJsonFixtures.actualDerivedCreatorGetWithVideoProfileItem)
        try {
            val items = fanbox.getCreatorDetail(FanboxCreatorId("fixture-video-creator")).profileItems

            assertEquals(
                listOf(
                    FanboxCreatorDetail.ProfileItem.Image(
                        id = "fixture-profile-image-1",
                        imageUrl = "https://example.invalid/profile-image-1",
                        thumbnailUrl = "https://example.invalid/profile-thumbnail-1",
                    ),
                    FanboxCreatorDetail.ProfileItem.Image(
                        id = "fixture-profile-image-2",
                        imageUrl = "https://example.invalid/profile-image-2",
                        thumbnailUrl = "https://example.invalid/profile-thumbnail-2",
                    ),
                    FanboxCreatorDetail.ProfileItem.Video(
                        id = "fixture-profile-video-1",
                        serviceProvider = "youtube",
                        videoId = "fixture-video-1",
                        thumbnailUrl = null,
                    ),
                ),
                items,
            )
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun syntheticUnknownIncompleteBlankAndLenientItemsPreserveTheirBoundaries() {
        val fixture = FanboxCreatorJsonFixtures.syntheticCreatorGet(
            """
            [
              {"id":"future-1","type":"gallery","future":{"value":1}},
              {"id":"incomplete-1","type":"video","serviceProvider":"youtube"},
              {"id":"blank-1","type":"video","serviceProvider":"youtube","videoId":""},
              {"id":123,"type":true}
            ]
            """.trimIndent(),
        )
        val actual = FanboxCreatorMapper().map(decodeFixture<FanboxCreatorDetailEntity>(fixture))

        val future = assertIs<FanboxCreatorDetail.ProfileItem.Unknown>(actual.profileItems[0])
        assertEquals("future-1", future.id)
        assertEquals("gallery", future.type)
        assertEquals(
            "1",
            createFanboxJson().parseToJsonElement(future.rawJson)
                .jsonObject["future"]
                ?.jsonObject
                ?.get("value")
                ?.jsonPrimitive
                ?.content,
        )

        assertEquals("incomplete-1", assertIs<FanboxCreatorDetail.ProfileItem.Unknown>(actual.profileItems[1]).id)
        assertEquals("blank-1", assertIs<FanboxCreatorDetail.ProfileItem.Unknown>(actual.profileItems[2]).id)
        val lenient = assertIs<FanboxCreatorDetail.ProfileItem.Unknown>(actual.profileItems[3])
        assertEquals("123", lenient.id)
        assertEquals("true", lenient.type)
    }

    @Test
    fun videoUrlSupportsKnownProvidersOnly() {
        val youtube = video("youtube", "youtube-id")
        val vimeo = video("vimeo", "vimeo-id")
        val future = video("future-provider", "future-id")

        assertEquals("https://www.youtube.com/watch?v=youtube-id", youtube.url)
        assertEquals("https://vimeo.com/vimeo-id", vimeo.url)
        assertNull(future.url)
    }

    @Test
    fun sealedProfileItemsRoundTripWithoutTypeCollision() {
        val formatter = createFanboxJson()
        val values = listOf<FanboxCreatorDetail.ProfileItem>(
            FanboxCreatorDetail.ProfileItem.Image("image-id", null, "https://example.invalid/image"),
            video("youtube", "video-id"),
            FanboxCreatorDetail.ProfileItem.Unknown("unknown-id", "future-type", """{"future":true}"""),
        )

        values.forEach { value ->
            val encoded = formatter.encodeToString(FanboxCreatorDetail.ProfileItem.serializer(), value)
            assertEquals(value, formatter.decodeFromString(FanboxCreatorDetail.ProfileItem.serializer(), encoded))
        }

        val encodedUnknown = formatter.encodeToString(
            FanboxCreatorDetail.ProfileItem.serializer(),
            values[2],
        )
        val unknownJson = formatter.parseToJsonElement(encodedUnknown).jsonObject
        assertEquals("unknown", unknownJson["type"]?.jsonPrimitive?.content)
        assertEquals("future-type", unknownJson["itemType"]?.jsonPrimitive?.content)
        assertFailsWith<SerializationException> {
            formatter.decodeFromString<FanboxCreatorDetail.ProfileItem>(
                """{"id":"missing-discriminator","imageUrl":null,"thumbnailUrl":null}""",
            )
        }
    }

    @Test
    fun directCreatorDetailConvertsInnerDecodeFailureToSchemaMismatch() = runBlocking {
        val fanbox = createFanbox(
            FanboxCreatorJsonFixtures.syntheticCreatorGet("""[{"id":[],"type":"image"}]"""),
        )
        try {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> {
                fanbox.getCreatorDetail(FanboxCreatorId("synthetic-creator"))
            }
            assertEquals(200, failure.statusCode)
            assertEquals("creator.get", failure.endpoint)
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun creatorSearchConvertsInnerDecodeFailureToSchemaMismatch() = runBlocking {
        val fanbox = createFanbox(
            FanboxCreatorJsonFixtures.syntheticCreatorSearch("""[{"id":[],"type":"image"}]"""),
        )
        try {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> {
                fanbox.searchCreators("synthetic", 0)
            }
            assertEquals(200, failure.statusCode)
            assertEquals("creator.search", failure.endpoint)
        } finally {
            fanbox.close()
        }
    }

    private fun video(
        serviceProvider: String,
        videoId: String,
    ) = FanboxCreatorDetail.ProfileItem.Video(
        id = "$serviceProvider-$videoId",
        serviceProvider = serviceProvider,
        videoId = videoId,
        thumbnailUrl = null,
    )

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
