package me.matsumo.fankt.fanbox.fixture

internal object FanboxCreatorJsonFixtures {

    /**
     * Whole-value-anonymized actual response captured from creator.get.
     */
    val actualCreatorGet =
        """
        {
          "body": {
            "user": {
              "userId": "91000001",
              "name": "Fixture Name 1",
              "iconUrl": "https://example.invalid/resource-1"
            },
            "creatorId": "fixture-creator-1",
            "description": "Fixture description 1",
            "hasAdultContent": false,
            "coverImageUrl": "https://example.invalid/resource-2",
            "profileLinks": [
              "https://www.youtube.com/fixture-known-1",
              "https://example.invalid/fixture-unknown-2"
            ],
            "profileItems": [
              {
                "id": "fixture-profile-item-1",
                "type": "image",
                "imageUrl": "https://example.invalid/resource-3",
                "thumbnailUrl": "https://example.invalid/resource-4"
              },
              {
                "id": "fixture-profile-item-2",
                "type": "image",
                "imageUrl": "https://example.invalid/resource-5",
                "thumbnailUrl": "https://example.invalid/resource-6"
              },
              {
                "id": "fixture-profile-item-3",
                "type": "image",
                "imageUrl": "https://example.invalid/resource-7",
                "thumbnailUrl": "https://example.invalid/resource-8"
              }
            ],
            "isFollowed": false,
            "isSupported": false,
            "isStopped": false,
            "isAcceptingRequest": false,
            "hasBoothShop": true,
            "hasPublishedPost": true,
            "category": "fixture-category-1"
          }
        }
        """.trimIndent()

    /**
     * Whole-value-anonymized actual response captured from plan.listCreator.
     */
    val actualCreatorPlanList =
        """
        {
          "body": [
            {
              "id": "94000001",
              "title": "Fixture Title 1",
              "fee": 1234,
              "description": "Fixture description 1",
              "coverImageUrl": "https://example.invalid/resource-1",
              "user": {
                "userId": "91000001",
                "name": "Fixture Name 1",
                "iconUrl": "https://example.invalid/resource-2"
              },
              "creatorId": "fixture-creator-1",
              "hasAdultContent": false,
              "paymentMethod": null,
              "perks": []
            },
            {
              "id": "94000002",
              "title": "Fixture Title 2",
              "fee": 1234,
              "description": "Fixture description 2",
              "coverImageUrl": "https://example.invalid/resource-3",
              "user": {
                "userId": "91000002",
                "name": "Fixture Name 2",
                "iconUrl": "https://example.invalid/resource-4"
              },
              "creatorId": "fixture-creator-2",
              "hasAdultContent": false,
              "paymentMethod": null,
              "perks": []
            }
          ]
        }
        """.trimIndent()
}
