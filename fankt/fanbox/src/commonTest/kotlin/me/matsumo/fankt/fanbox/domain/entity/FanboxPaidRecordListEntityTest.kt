package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxUserMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxPaidRecordListEntityTest {

    private val formatter = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodeAndMapWrappedPayments() {
        val entity = formatter.decodeFromString<FanboxPaidRecordListEntity>(responseJson)
        val mapper = FanboxUserMapper(
            postMapper = FanboxPostMapper(),
            creatorMapper = FanboxCreatorMapper(),
        )

        val payments = mapper.map(entity)

        assertEquals(1, payments.size)
        assertEquals("test-payment-id", payments[0].id)
        assertEquals(500, payments[0].paidAmount)
    }

    private companion object {
        val responseJson =
            """
            {
              "body": {
                "payments": [
                  {
                    "id": "test-payment-id",
                    "creator": {
                      "user": {
                        "userId": "1",
                        "name": "Test user",
                        "iconUrl": "https://example.com/icon.png"
                      },
                      "isActive": true,
                      "creatorId": "test-creator"
                    },
                    "paidAmount": 500,
                    "paymentMethod": "card",
                    "breakdown": null,
                    "paymentDatetime": "2026-07-13T00:00:00+00:00"
                  }
                ]
              }
            }
            """.trimIndent()
    }
}
