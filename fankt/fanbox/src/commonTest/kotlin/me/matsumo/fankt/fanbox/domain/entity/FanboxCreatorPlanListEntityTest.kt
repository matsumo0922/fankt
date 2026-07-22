package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPlanId
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxCreatorPlanListEntityTest {

    private val formatter = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodeAndMapWrappedPlans() {
        val entity = formatter.decodeFromString<FanboxCreatorPlanListEntity>(responseJson)

        val plans = FanboxCreatorMapper().map(entity)

        assertEquals(1, plans.size)
        assertEquals(FanboxPlanId("test-plan-id"), plans[0].id)
        assertEquals("Test plan", plans[0].title)
    }

    private companion object {
        val responseJson =
            """
            {
              "body": {
                "plans": [
                  {
                    "id": "test-plan-id",
                    "title": "Test plan",
                    "fee": 500,
                    "description": "Test description",
                    "coverImageUrl": "https://example.com/cover.png",
                    "user": {
                      "userId": "1",
                      "name": "Test user",
                      "iconUrl": "https://example.com/icon.png"
                    },
                    "creatorId": "test-creator",
                    "hasAdultContent": false,
                    "paymentMethod": "card",
                    "perks": []
                  }
                ]
              }
            }
            """.trimIndent()
    }
}
