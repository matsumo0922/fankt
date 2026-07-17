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
}
