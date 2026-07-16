package me.matsumo.fankt.fanbox.fixture

internal object FanboxPaymentJsonFixtures {
    val paidRecords =
        """
        {
          "body": {
            "payments": [
              {
                "creator": {
                  "creatorId": "fixture-paid-creator-1",
                  "user": {
                    "iconUrl": "https://example.invalid/paid-user-1.png",
                    "name": "Fixture Paid User 1",
                    "userId": "94000001"
                  }
                },
                "id": "fixture-paid-record-1",
                "paidAmount": 987654321,
                "paymentDatetime": "2000-01-01T00:00:00+00:00",
                "paymentMethod": "card"
              }
            ]
          }
        }
        """.trimIndent()
}
