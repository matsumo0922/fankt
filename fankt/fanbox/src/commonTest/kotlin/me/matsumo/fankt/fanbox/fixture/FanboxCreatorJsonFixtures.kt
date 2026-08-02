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
     * Hybrid fixture for creator.get profile item coverage.
     *
     * The image/image/video field presence, string representation, and order are derived from a
     * saved actual response. The creator envelope is the existing whole-value-anonymized fixture
     * shape. This is not claimed to be one complete original response. Vimeo, unknown items, and
     * malformed values are not part of the observed fragment.
     */
    val actualDerivedCreatorGetWithVideoProfileItem =
        """
        {
          "body": {
            "user": {
              "userId": "92000001",
              "name": "Fixture Video Creator",
              "iconUrl": "https://example.invalid/video-creator-icon"
            },
            "creatorId": "fixture-video-creator",
            "description": "Fixture video creator description",
            "hasAdultContent": false,
            "coverImageUrl": "https://example.invalid/video-creator-cover",
            "profileLinks": [],
            "profileItems": [
              {
                "id": "fixture-profile-image-1",
                "type": "image",
                "imageUrl": "https://example.invalid/profile-image-1",
                "thumbnailUrl": "https://example.invalid/profile-thumbnail-1"
              },
              {
                "id": "fixture-profile-image-2",
                "type": "image",
                "imageUrl": "https://example.invalid/profile-image-2",
                "thumbnailUrl": "https://example.invalid/profile-thumbnail-2"
              },
              {
                "id": "fixture-profile-video-1",
                "type": "video",
                "serviceProvider": "youtube",
                "videoId": "fixture-video-1"
              }
            ],
            "isFollowed": false,
            "isSupported": false,
            "isStopped": false,
            "isAcceptingRequest": false,
            "hasBoothShop": false,
            "hasPublishedPost": true,
            "category": "fixture-video-category"
          }
        }
        """.trimIndent()

    /**
     * Whole-value-anonymized actual response captured from plan.listCreator.
     */
    val actualCreatorPlanList =
        """
        {
          "body": {
            "plans": [
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
        }
        """.trimIndent()

    /** Synthetic creator.get probe; it does not claim production schema compatibility. */
    fun syntheticCreatorGet(profileItems: String): String =
        """
        {
          "body": ${syntheticCreatorBody(profileItems)}
        }
        """.trimIndent()

    /** Synthetic creator.search probe; it does not claim production schema compatibility. */
    fun syntheticCreatorSearch(profileItems: String): String =
        """
        {
          "body": {
            "count": 1,
            "creators": [${syntheticCreatorBody(profileItems)}],
            "nextPage": null
          }
        }
        """.trimIndent()

    private fun syntheticCreatorBody(profileItems: String): String =
        """
        {
          "user": null,
          "creatorId": "synthetic-creator",
          "description": "Synthetic creator probe",
          "hasAdultContent": false,
          "coverImageUrl": null,
          "profileLinks": [],
          "profileItems": $profileItems,
          "isFollowed": false,
          "isSupported": false,
          "isStopped": false,
          "isAcceptingRequest": false,
          "hasBoothShop": false
        }
        """.trimIndent()
}
