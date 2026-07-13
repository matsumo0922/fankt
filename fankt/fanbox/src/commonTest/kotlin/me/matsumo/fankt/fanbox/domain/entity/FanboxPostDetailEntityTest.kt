package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxPostDetailEntityTest {

    private val formatter = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodeAndMapWrappedPost() {
        val entity = formatter.decodeFromString<FanboxPostDetailEntity>(responseJson)

        val post = FanboxPostMapper().map(entity)

        assertEquals(FanboxPostId("12244258"), post.id)
        assertEquals("Test post", post.title)
    }

    private companion object {
        val responseJson =
            """
            {
              "body": {
                "post": {
                  "body": null,
                  "commentCount": 0,
                  "creatorId": "test-creator",
                  "excerpt": "Test excerpt",
                  "feeRequired": 0,
                  "hasAdultContent": false,
                  "id": "12244258",
                  "imageForShare": "https://example.com/share.jpg",
                  "isLiked": false,
                  "isRestricted": false,
                  "likeCount": 0,
                  "nextPost": null,
                  "prevPost": null,
                  "publishedDatetime": "2026-07-13T00:00:00+00:00",
                  "tags": [],
                  "title": "Test post",
                  "type": "text",
                  "updatedDatetime": "2026-07-13T00:00:00+00:00",
                  "coverImageUrl": null,
                  "user": null
                }
              }
            }
            """.trimIndent()
    }
}
