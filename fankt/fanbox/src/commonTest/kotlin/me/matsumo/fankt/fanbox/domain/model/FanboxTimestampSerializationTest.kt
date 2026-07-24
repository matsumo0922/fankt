@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.matsumo.fankt.fanbox.createFanboxJson
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FanboxTimestampSerializationTest {

    private val formatter = createFanboxJson()

    @Test
    fun postTimestampsRoundTripAsStdlibInstant() {
        val publishedAt = Instant.parse("2025-01-02T03:04:05+09:00")
        val updatedAt = Instant.parse("2025-01-02T03:05:06-04:00")
        val expected = FanboxPost(
            id = FanboxPostId("fixture-post"),
            title = "Fixture post",
            cover = null,
            user = null,
            excerpt = "Fixture excerpt",
            feeRequired = 0,
            hasAdultContent = false,
            isLiked = false,
            isRestricted = false,
            likeCount = 0,
            commentCount = 0,
            tags = emptyList(),
            publishedDatetime = publishedAt,
            updatedDatetime = updatedAt,
        )

        val encoded = formatter.encodeToString(expected)
        val encodedObject = formatter.parseToJsonElement(encoded).jsonObject
        val actual = formatter.decodeFromString<FanboxPost>(encoded)

        assertEquals(publishedAt.toString(), encodedObject.getValue("publishedDatetime").jsonPrimitive.content)
        assertEquals(updatedAt.toString(), encodedObject.getValue("updatedDatetime").jsonPrimitive.content)
        assertEquals(expected, actual)
    }

    @Test
    fun postDetailTimestampsRoundTripAsStdlibInstant() {
        val publishedAt = Instant.parse("2025-01-02T03:04:05.678+09:00")
        val updatedAt = Instant.parse("2025-01-02T03:05:06.789-04:00")
        val expected = FanboxPostDetail(
            id = FanboxPostId("fixture-detail"),
            title = "Fixture post detail",
            body = FanboxPostDetail.Body.Text("Fixture body"),
            coverImageUrl = null,
            commentCount = 0,
            excerpt = "Fixture excerpt",
            feeRequired = 0,
            hasAdultContent = false,
            imageForShare = "",
            isLiked = false,
            isRestricted = false,
            likeCount = 0,
            tags = emptyList(),
            updatedDatetime = updatedAt,
            publishedDatetime = publishedAt,
            nextPost = null,
            prevPost = null,
            user = null,
        )

        val encoded = formatter.encodeToString(expected)
        val encodedObject = formatter.parseToJsonElement(encoded).jsonObject
        val actual = formatter.decodeFromString<FanboxPostDetail>(encoded)

        assertEquals(publishedAt.toString(), encodedObject.getValue("publishedDatetime").jsonPrimitive.content)
        assertEquals(updatedAt.toString(), encodedObject.getValue("updatedDatetime").jsonPrimitive.content)
        assertEquals(expected, actual)
    }
}
