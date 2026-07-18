package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
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
    fun legacyBodyVariantsRoundTrip() {
        val bodies: List<FanboxPostDetail.Body> = listOf(
            FanboxPostDetail.Body.Text("Fixture legacy text"),
            FanboxPostDetail.Body.Video("youtube", "fixture-video-1"),
            FanboxPostDetail.Body.Html("<p>Fixture legacy HTML</p>"),
        )

        bodies.forEach { expected ->
            val actual = formatter.decodeFromString<FanboxPostDetail.Body>(formatter.encodeToString(expected))
            assertEquals(expected, actual)
            assertEquals(emptyList(), actual.imageItems)
            assertEquals(emptyList(), actual.fileItems)
        }
    }

    @Test
    fun legacyVideoRestoresSupportedProviderUrls() {
        assertEquals(
            "https://www.youtube.com/watch?v=fixture-video",
            FanboxPostDetail.Body.Video("youtube", "fixture-video").url,
        )
        assertEquals(
            "https://vimeo.com/fixture-video",
            FanboxPostDetail.Body.Video("vimeo", "fixture-video").url,
        )
        assertNull(FanboxPostDetail.Body.Video("future-provider", "fixture-video").url)
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
    fun articleLinkRoundTripsWithoutDiscriminatorConflict() {
        val expected: FanboxPostDetail.Body.Article.Block =
            FanboxPostDetail.Body.Article.Block.Link(
                html = "<div>Fixture link</div>",
                post = null,
                type = "default",
                url = "https://example.invalid/link",
            )

        val encoded = formatter.encodeToString(expected)
        val encodedObject = formatter.parseToJsonElement(encoded).jsonObject
        val actual = formatter.decodeFromString<FanboxPostDetail.Body.Article.Block>(encoded)

        assertEquals(expected, actual)
        assertEquals("default", encodedObject["linkType"]?.jsonPrimitive?.content)
        assertEquals(
            "https://example.invalid/link",
            encodedObject["url"]?.jsonPrimitive?.content,
        )
        assertEquals(true, encodedObject.containsKey("type"))
    }

    @Test
    fun legacyArticleLinkDecodesWithCompatibilityDefaults() {
        val current: FanboxPostDetail.Body.Article.Block =
            FanboxPostDetail.Body.Article.Block.Link(
                html = "<div>Fixture legacy link</div>",
                post = null,
                type = "html",
                url = "https://example.invalid/current-link",
            )
        val currentObject = formatter.parseToJsonElement(formatter.encodeToString(current)).jsonObject
        val legacyJson = JsonObject(currentObject - "linkType" - "url").toString()

        val actual = formatter.decodeFromString<FanboxPostDetail.Body.Article.Block>(legacyJson)

        assertEquals(
            FanboxPostDetail.Body.Article.Block.Link(
                html = "<div>Fixture legacy link</div>",
                post = null,
            ),
            actual,
        )
    }

    @Test
    fun articleTextMetadataRoundTripsAsSealedBlock() {
        val expected: FanboxPostDetail.Body.Article.Block =
            FanboxPostDetail.Body.Article.Block.Text(
                text = "Fixture text",
                styles = listOf(
                    FanboxPostDetail.Body.Article.Block.Text.StyleSpan("bold", 0, 7),
                ),
                links = listOf(
                    FanboxPostDetail.Body.Article.Block.Text.LinkSpan(
                        8,
                        4,
                        "https://example.invalid/inline",
                    ),
                ),
                isHeader = true,
            )

        val encoded = formatter.encodeToString(expected)
        val encodedObject = formatter.parseToJsonElement(encoded).jsonObject
        val actual = formatter.decodeFromString<FanboxPostDetail.Body.Article.Block>(encoded)

        assertEquals(expected, actual)
        assertEquals(true, encodedObject.containsKey("type"))
    }

    @Test
    fun legacyArticleTextDecodesWithCompatibilityDefaults() {
        val current: FanboxPostDetail.Body.Article.Block =
            FanboxPostDetail.Body.Article.Block.Text(
                text = "Fixture legacy article text",
                styles = listOf(
                    FanboxPostDetail.Body.Article.Block.Text.StyleSpan("bold", 0, 7),
                ),
                links = listOf(
                    FanboxPostDetail.Body.Article.Block.Text.LinkSpan(
                        8,
                        4,
                        "https://example.invalid/current-inline",
                    ),
                ),
                isHeader = true,
            )
        val currentObject = formatter.parseToJsonElement(formatter.encodeToString(current)).jsonObject
        val legacyJson = JsonObject(currentObject - "styles" - "links" - "isHeader").toString()

        val actual = formatter.decodeFromString<FanboxPostDetail.Body.Article.Block>(legacyJson)

        assertEquals(
            FanboxPostDetail.Body.Article.Block.Text("Fixture legacy article text"),
            actual,
        )
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
