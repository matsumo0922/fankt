package me.matsumo.fankt.fanbox.fixture

internal object FanboxTolerantListJsonFixtures {
    val timelineMixed =
        """
        {
          "body": {
            "items": [
              ${post("tolerant-post-1", "First valid post")},
              {
                "id": "broken-post",
                "csrfToken": "fixture-credential-must-be-redacted",
                "excerpt": "private item content must not be logged by default"
              },
              ${post("tolerant-post-2", "Second valid post")}
            ],
            "nextUrl": "https://api.fanbox.cc/post.listHome?maxId=tolerant-post-2&maxPublishedDatetime=2000-01-02T00%3A00%3A00%2B00%3A00&limit=3"
          }
        }
        """.trimIndent()

    val bellMixed =
        """
        {
          "body": {
            "items": [
              ${bell("tolerant-bell-1", 1)},
              {
                "id": "broken-bell",
                "isUnread": false,
                "notifiedDatetime": "2000-01-02T00:00:00+00:00",
                "type": "post_comment_like",
                "postCommentBody": "missing count",
                "creatorId": "fixture-creator",
                "postId": "tolerant-post-1"
              },
              ${bell("tolerant-bell-2", 2)}
            ],
            "nextUrl": "https://api.fanbox.cc/bell.list?page=2"
          }
        }
        """.trimIndent()

    val commentsWithBrokenReply =
        """
        {
          "body": {
            "viewMode": "default",
            "commentList": {
              "items": [
                ${comment(
            id = "root-comment",
            body = "root",
            replies = """
              [
                ${comment("reply-1", "first reply")},
                {
                  "id": "broken-reply",
                  "createdDatetime": "2000-01-02T00:02:00+00:00"
                },
                ${comment("reply-2", "second reply")}
              ]
            """.trimIndent(),
        )}
              ],
              "nextUrl": null
            }
          }
        }
        """.trimIndent()

    val supportingPlansMixed =
        """
        {
          "body": {
            "plans": [
              ${plan("tolerant-plan-1", "First plan")},
              {
                "id": "broken-plan",
                "description": "missing title",
                "fee": 500,
                "hasAdultContent": false
              },
              ${plan("tolerant-plan-2", "Second plan")}
            ]
          }
        }
        """.trimIndent()

    val supportingPlanInvalidUserId =
        """
        {
          "body": {
            "plans": [
              {
                "coverImageUrl": null,
                "creatorId": "fixture-creator",
                "description": "fixture plan",
                "fee": 500,
                "hasAdultContent": false,
                "id": "invalid-user-plan",
                "paymentMethod": null,
                "title": "Invalid user plan",
                "user": {
                  "iconUrl": null,
                  "name": "Fixture user",
                  "userId": "not-a-number"
                }
              }
            ]
          }
        }
        """.trimIndent()

    val creatorsMixed =
        """
        {
          "body": {
            "creators": [
              ${creator("tolerant-creator-1")},
              { "creatorId": "broken-creator" },
              ${creator("tolerant-creator-2")}
            ]
          }
        }
        """.trimIndent()

    /** Synthetic probe for mapper-time profile item decode failure in tolerant creator lists. */
    val creatorsWithInvalidProfileItem =
        """
        {
          "body": {
            "creators": [
              ${creator("profile-creator-1")},
              ${creator("profile-creator-invalid", "[{\"id\": [], \"type\": \"image\"}]")},
              ${creator("profile-creator-2")}
            ]
          }
        }
        """.trimIndent()

    private fun post(id: String, title: String): String =
        """
        {
          "commentCount": 0,
          "cover": null,
          "creatorId": "fixture-creator",
          "excerpt": "fixture excerpt",
          "feeRequired": 0,
          "hasAdultContent": false,
          "id": "$id",
          "isLiked": false,
          "isRestricted": false,
          "likeCount": 0,
          "publishedDatetime": "2000-01-02T00:00:00+00:00",
          "tags": [],
          "title": "$title",
          "updatedDatetime": "2000-01-02T00:00:00+00:00",
          "user": null
        }
        """.trimIndent()

    private fun bell(id: String, count: Int): String =
        """
        {
          "id": "$id",
          "isUnread": false,
          "notifiedDatetime": "2000-01-02T00:00:00+00:00",
          "type": "post_comment_like",
          "postCommentBody": "fixture comment",
          "creatorId": "fixture-creator",
          "postId": "tolerant-post-$count",
          "count": $count
        }
        """.trimIndent()

    private fun comment(
        id: String,
        body: String,
        replies: String = "[]",
    ): String =
        """
        {
          "body": "$body",
          "createdDatetime": "2000-01-02T00:00:00+00:00",
          "id": "$id",
          "isLiked": false,
          "isOwn": false,
          "likeCount": 0,
          "parentCommentId": "0",
          "rootCommentId": "0",
          "user": null,
          "replies": $replies
        }
        """.trimIndent()

    private fun plan(id: String, title: String): String =
        """
        {
          "coverImageUrl": null,
          "creatorId": "fixture-creator",
          "description": "fixture plan",
          "fee": 500,
          "hasAdultContent": false,
          "id": "$id",
          "paymentMethod": null,
          "title": "$title",
          "user": null
        }
        """.trimIndent()

    private fun creator(
        id: String,
        profileItems: String = "[]",
    ): String =
        """
        {
          "coverImageUrl": null,
          "creatorId": "$id",
          "description": "fixture creator",
          "hasAdultContent": false,
          "hasBoothShop": false,
          "isAcceptingRequest": false,
          "isFollowed": true,
          "isStopped": false,
          "isSupported": false,
          "profileItems": $profileItems,
          "profileLinks": [],
          "user": null
        }
        """.trimIndent()
}
