package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxCreatorPostsPaginateEntityTest {

    private val formatter = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodeAndMapWrappedPageUrls() {
        val entity = formatter.decodeFromString<FanboxCreatorPostsPaginateEntity>(responseJson)

        val cursors = FanboxPostMapper().map(entity)

        assertEquals(2, cursors.size)
        assertEquals("20260101", cursors[0].maxId)
        assertEquals("20260201", cursors[1].maxId)
    }

    private companion object {
        val responseJson =
            """
            {
              "body": {
                "pageUrls": [
                  "https://api.fanbox.cc/post.listCreator?creatorId=test-creator&maxId=20260101&limit=10",
                  "https://api.fanbox.cc/post.listCreator?creatorId=test-creator&maxId=20260201&limit=10"
                ]
              }
            }
            """.trimIndent()
    }
}
