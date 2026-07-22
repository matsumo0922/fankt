package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxCreatorPostListEntityTest {

    private val formatter = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodeAndMapWrappedPosts() {
        val entity = formatter.decodeFromString<FanboxCreatorPostItemsEntity>(responseJson)

        val posts = FanboxPostMapper().map(entity, nextCursor = null).contents

        assertEquals(1, posts.size)
        assertEquals(FanboxPostId("12244258"), posts[0].id)
        assertEquals("Test post", posts[0].title)
    }

    private companion object {
        val responseJson =
            """
            {
              "body": {
                "posts": [
                  {
                    "id": "12244258",
                    "title": "Test post",
                    "feeRequired": 0,
                    "publishedDatetime": "2026-07-13T00:00:00+00:00",
                    "updatedDatetime": "2026-07-13T00:00:00+00:00",
                    "tags": [],
                    "isLiked": false,
                    "likeCount": 0,
                    "isCommentingRestricted": false,
                    "commentCount": 0,
                    "isRestricted": false,
                    "user": {
                      "userId": "1",
                      "name": "Test user",
                      "iconUrl": "https://example.com/icon.png"
                    },
                    "creatorId": "test-creator",
                    "hasAdultContent": false,
                    "cover": {
                      "type": "cover_image",
                      "url": "https://example.com/cover.png"
                    },
                    "excerpt": "Test excerpt",
                    "isPinned": false
                  }
                ]
              }
            }
            """.trimIndent()
    }
}
