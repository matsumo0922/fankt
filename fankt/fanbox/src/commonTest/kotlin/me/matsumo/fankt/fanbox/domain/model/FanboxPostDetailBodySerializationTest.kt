package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.matsumo.fankt.fanbox.createFanboxJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FanboxPostDetailBodySerializationTest {

    private val formatter = createFanboxJson()

    @Test
    fun unknownBodyRoundTripsWithoutDiscriminatorConflict() {
        val expected: FanboxPostDetail.Body = FanboxPostDetail.Body.Unknown(
            type = "video",
            rawBodyJson = """{"videoId":"fixture-video-1"}""",
        )

        val encoded = formatter.encodeToString(expected)
        val actual = formatter.decodeFromString<FanboxPostDetail.Body>(encoded)

        assertEquals(expected, actual)
        assertEquals("video", formatter.parseToJsonElement(encoded).jsonObject["postType"]?.jsonPrimitive?.content)
    }

    @Test
    fun legacyUnknownObjectDecodesWithCompatibilityDefaults() {
        val encoded = formatter.encodeToString<FanboxPostDetail.Body>(FanboxPostDetail.Body.Unknown())
        val discriminator = formatter.parseToJsonElement(encoded).jsonObject.getValue("type")
        val legacyJson = buildJsonObject {
            put("type", discriminator)
        }.toString()

        val actual = formatter.decodeFromString<FanboxPostDetail.Body>(legacyJson)

        assertEquals(FanboxPostDetail.Body.Unknown(type = "unknown", rawBodyJson = null), actual)
    }

    @Test
    fun articleEmbedAndUnknownBlocksRoundTrip() {
        val expected: FanboxPostDetail.Body = FanboxPostDetail.Body.Article(
            listOf(
                FanboxPostDetail.Body.Article.Block.Embed("youtube", "fixture-video"),
                FanboxPostDetail.Body.Article.Block.Unknown("{\"type\":\"future-block\"}"),
            ),
        )

        val actual = formatter.decodeFromString<FanboxPostDetail.Body>(formatter.encodeToString(expected))

        assertEquals(expected, actual)
    }

    @Test
    fun embedRestoresProviderUrls() {
        val cases = mapOf(
            "twitter" to "https://twitter.com/_/status/fixture-content",
            "youtube" to "https://www.youtube.com/watch?v=fixture-content",
            "vimeo" to "https://vimeo.com/fixture-content",
            "soundcloud" to "https://soundcloud.com/fixture-content",
            "google_forms" to
                "https://docs.google.com/forms/d/e/fixture-content/viewform?usp=sf_link",
            "fanbox" to "https://www.pixiv.net/fanbox/fixture-content",
        )

        cases.forEach { (provider, expectedUrl) ->
            assertEquals(
                expectedUrl,
                FanboxPostDetail.Body.Article.Block.Embed(provider, "fixture-content").url,
            )
        }
        assertNull(FanboxPostDetail.Body.Article.Block.Embed("future-provider", "fixture-content").url)
    }
}
