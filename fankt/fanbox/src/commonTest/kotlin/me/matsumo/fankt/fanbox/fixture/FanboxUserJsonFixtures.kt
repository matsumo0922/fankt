package me.matsumo.fankt.fanbox.fixture

internal object FanboxUserJsonFixtures {

    /**
     * Whole-value-anonymized actual page-one response captured from bell.list.
     */
    val actualBellPostPublishedPage =
        """
        {
          "body": {
            "items": [
              {
                "id": "fixture-bell-1",
                "notifiedDatetime": "2000-01-01T00:01:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-2",
                  "title": "Fixture Title 1",
                  "feeRequired": 90000001,
                  "publishedDatetime": "2000-01-01T00:02:00+00:00",
                  "updatedDatetime": "2000-01-01T00:03:00+00:00",
                  "tags": [
                    "Fixture Tag 1",
                    "Fixture Tag 2",
                    "Fixture Tag 3"
                  ],
                  "isLiked": false,
                  "likeCount": 1,
                  "isCommentingRestricted": false,
                  "commentCount": 2,
                  "isRestricted": true,
                  "user": {
                    "userId": "91000001",
                    "name": "Fixture Name 1",
                    "iconUrl": "https://example.invalid/resource-1"
                  },
                  "creatorId": "fixture-creator-1",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-2"
                  },
                  "excerpt": "Fixture text 1"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-3",
                "notifiedDatetime": "2000-01-01T00:04:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-4",
                  "title": "Fixture Title 2",
                  "feeRequired": 90000002,
                  "publishedDatetime": "2000-01-01T00:05:00+00:00",
                  "updatedDatetime": "2000-01-01T00:06:00+00:00",
                  "tags": [],
                  "isLiked": false,
                  "likeCount": 3,
                  "isCommentingRestricted": true,
                  "commentCount": 4,
                  "isRestricted": false,
                  "user": {
                    "userId": "91000002",
                    "name": "Fixture Name 2",
                    "iconUrl": "https://example.invalid/resource-3"
                  },
                  "creatorId": "fixture-creator-2",
                  "hasAdultContent": false,
                  "cover": null,
                  "excerpt": "Fixture text 2"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-5",
                "notifiedDatetime": "2000-01-01T00:07:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-6",
                  "title": "Fixture Title 3",
                  "feeRequired": 90000003,
                  "publishedDatetime": "2000-01-01T00:08:00+00:00",
                  "updatedDatetime": "2000-01-01T00:09:00+00:00",
                  "tags": [
                    "Fixture Tag 4",
                    "Fixture Tag 5",
                    "Fixture Tag 6"
                  ],
                  "isLiked": false,
                  "likeCount": 5,
                  "isCommentingRestricted": false,
                  "commentCount": 6,
                  "isRestricted": true,
                  "user": {
                    "userId": "91000003",
                    "name": "Fixture Name 3",
                    "iconUrl": "https://example.invalid/resource-4"
                  },
                  "creatorId": "fixture-creator-3",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-5"
                  },
                  "excerpt": "Fixture text 3"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-7",
                "notifiedDatetime": "2000-01-01T00:10:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-8",
                  "title": "Fixture Title 4",
                  "feeRequired": 90000004,
                  "publishedDatetime": "2000-01-01T00:11:00+00:00",
                  "updatedDatetime": "2000-01-01T00:12:00+00:00",
                  "tags": [
                    "Fixture Tag 7",
                    "Fixture Tag 8",
                    "Fixture Tag 9",
                    "Fixture Tag 10",
                    "Fixture Tag 11",
                    "Fixture Tag 12"
                  ],
                  "isLiked": false,
                  "likeCount": 7,
                  "isCommentingRestricted": true,
                  "commentCount": 8,
                  "isRestricted": false,
                  "user": {
                    "userId": "91000004",
                    "name": "Fixture Name 4",
                    "iconUrl": "https://example.invalid/resource-6"
                  },
                  "creatorId": "fixture-creator-4",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-7"
                  },
                  "excerpt": "Fixture text 4"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-9",
                "notifiedDatetime": "2000-01-01T00:13:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-10",
                  "title": "Fixture Title 5",
                  "feeRequired": 90000005,
                  "publishedDatetime": "2000-01-01T00:14:00+00:00",
                  "updatedDatetime": "2000-01-01T00:15:00+00:00",
                  "tags": [
                    "Fixture Tag 13",
                    "Fixture Tag 14",
                    "Fixture Tag 15",
                    "Fixture Tag 16"
                  ],
                  "isLiked": false,
                  "likeCount": 9,
                  "isCommentingRestricted": false,
                  "commentCount": 10,
                  "isRestricted": true,
                  "user": {
                    "userId": "91000005",
                    "name": "Fixture Name 5",
                    "iconUrl": "https://example.invalid/resource-8"
                  },
                  "creatorId": "fixture-creator-5",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-9"
                  },
                  "excerpt": "Fixture text 5"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-11",
                "notifiedDatetime": "2000-01-01T00:16:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-12",
                  "title": "Fixture Title 6",
                  "feeRequired": 90000006,
                  "publishedDatetime": "2000-01-01T00:17:00+00:00",
                  "updatedDatetime": "2000-01-01T00:18:00+00:00",
                  "tags": [
                    "Fixture Tag 17",
                    "Fixture Tag 18",
                    "Fixture Tag 19",
                    "Fixture Tag 20"
                  ],
                  "isLiked": false,
                  "likeCount": 11,
                  "isCommentingRestricted": false,
                  "commentCount": 12,
                  "isRestricted": false,
                  "user": {
                    "userId": "91000006",
                    "name": "Fixture Name 6",
                    "iconUrl": "https://example.invalid/resource-10"
                  },
                  "creatorId": "fixture-creator-6",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-11"
                  },
                  "excerpt": "Fixture text 6"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-13",
                "notifiedDatetime": "2000-01-01T00:19:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-14",
                  "title": "Fixture Title 7",
                  "feeRequired": 90000007,
                  "publishedDatetime": "2000-01-01T00:20:00+00:00",
                  "updatedDatetime": "2000-01-01T00:21:00+00:00",
                  "tags": [],
                  "isLiked": false,
                  "likeCount": 13,
                  "isCommentingRestricted": false,
                  "commentCount": 14,
                  "isRestricted": true,
                  "user": {
                    "userId": "91000007",
                    "name": "Fixture Name 7",
                    "iconUrl": "https://example.invalid/resource-12"
                  },
                  "creatorId": "fixture-creator-7",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-13"
                  },
                  "excerpt": "Fixture text 7"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-15",
                "notifiedDatetime": "2000-01-01T00:22:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-16",
                  "title": "Fixture Title 8",
                  "feeRequired": 90000008,
                  "publishedDatetime": "2000-01-01T00:23:00+00:00",
                  "updatedDatetime": "2000-01-01T00:24:00+00:00",
                  "tags": [
                    "Fixture Tag 21",
                    "Fixture Tag 22"
                  ],
                  "isLiked": false,
                  "likeCount": 15,
                  "isCommentingRestricted": false,
                  "commentCount": 16,
                  "isRestricted": false,
                  "user": {
                    "userId": "91000008",
                    "name": "Fixture Name 8",
                    "iconUrl": "https://example.invalid/resource-14"
                  },
                  "creatorId": "fixture-creator-8",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-15"
                  },
                  "excerpt": "Fixture text 8"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-17",
                "notifiedDatetime": "2000-01-01T00:25:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-18",
                  "title": "Fixture Title 9",
                  "feeRequired": 90000009,
                  "publishedDatetime": "2000-01-01T00:26:00+00:00",
                  "updatedDatetime": "2000-01-01T00:27:00+00:00",
                  "tags": [
                    "Fixture Tag 23",
                    "Fixture Tag 24",
                    "Fixture Tag 25",
                    "Fixture Tag 26"
                  ],
                  "isLiked": false,
                  "likeCount": 17,
                  "isCommentingRestricted": false,
                  "commentCount": 18,
                  "isRestricted": true,
                  "user": {
                    "userId": "91000009",
                    "name": "Fixture Name 9",
                    "iconUrl": "https://example.invalid/resource-16"
                  },
                  "creatorId": "fixture-creator-9",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-17"
                  },
                  "excerpt": "Fixture text 9"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-19",
                "notifiedDatetime": "2000-01-01T00:28:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-20",
                  "title": "Fixture Title 10",
                  "feeRequired": 90000010,
                  "publishedDatetime": "2000-01-01T00:29:00+00:00",
                  "updatedDatetime": "2000-01-01T00:30:00+00:00",
                  "tags": [
                    "Fixture Tag 27",
                    "Fixture Tag 28",
                    "Fixture Tag 29",
                    "Fixture Tag 30",
                    "Fixture Tag 31",
                    "Fixture Tag 32"
                  ],
                  "isLiked": false,
                  "likeCount": 19,
                  "isCommentingRestricted": true,
                  "commentCount": 20,
                  "isRestricted": false,
                  "user": {
                    "userId": "91000010",
                    "name": "Fixture Name 10",
                    "iconUrl": "https://example.invalid/resource-18"
                  },
                  "creatorId": "fixture-creator-10",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-19"
                  },
                  "excerpt": "Fixture text 10"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-21",
                "notifiedDatetime": "2000-01-01T00:31:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-22",
                  "title": "Fixture Title 11",
                  "feeRequired": 90000011,
                  "publishedDatetime": "2000-01-01T00:32:00+00:00",
                  "updatedDatetime": "2000-01-01T00:33:00+00:00",
                  "tags": [
                    "Fixture Tag 33",
                    "Fixture Tag 34",
                    "Fixture Tag 35",
                    "Fixture Tag 36"
                  ],
                  "isLiked": false,
                  "likeCount": 21,
                  "isCommentingRestricted": false,
                  "commentCount": 22,
                  "isRestricted": true,
                  "user": {
                    "userId": "91000011",
                    "name": "Fixture Name 11",
                    "iconUrl": "https://example.invalid/resource-20"
                  },
                  "creatorId": "fixture-creator-11",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-21"
                  },
                  "excerpt": "Fixture text 11"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-23",
                "notifiedDatetime": "2000-01-01T00:34:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-24",
                  "title": "Fixture Title 12",
                  "feeRequired": 90000012,
                  "publishedDatetime": "2000-01-01T00:35:00+00:00",
                  "updatedDatetime": "2000-01-01T00:36:00+00:00",
                  "tags": [
                    "Fixture Tag 37",
                    "Fixture Tag 38",
                    "Fixture Tag 39",
                    "Fixture Tag 40"
                  ],
                  "isLiked": false,
                  "likeCount": 23,
                  "isCommentingRestricted": false,
                  "commentCount": 24,
                  "isRestricted": false,
                  "user": {
                    "userId": "91000012",
                    "name": "Fixture Name 12",
                    "iconUrl": "https://example.invalid/resource-22"
                  },
                  "creatorId": "fixture-creator-12",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-23"
                  },
                  "excerpt": "Fixture text 12"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-25",
                "notifiedDatetime": "2000-01-01T00:37:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-26",
                  "title": "Fixture Title 13",
                  "feeRequired": 90000013,
                  "publishedDatetime": "2000-01-01T00:38:00+00:00",
                  "updatedDatetime": "2000-01-01T00:39:00+00:00",
                  "tags": [],
                  "isLiked": false,
                  "likeCount": 25,
                  "isCommentingRestricted": true,
                  "commentCount": 26,
                  "isRestricted": false,
                  "user": {
                    "userId": "91000013",
                    "name": "Fixture Name 13",
                    "iconUrl": "https://example.invalid/resource-24"
                  },
                  "creatorId": "fixture-creator-13",
                  "hasAdultContent": false,
                  "cover": null,
                  "excerpt": "Fixture text 13"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-27",
                "notifiedDatetime": "2000-01-01T00:40:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-28",
                  "title": "Fixture Title 14",
                  "feeRequired": 90000014,
                  "publishedDatetime": "2000-01-01T00:41:00+00:00",
                  "updatedDatetime": "2000-01-01T00:42:00+00:00",
                  "tags": [],
                  "isLiked": false,
                  "likeCount": 27,
                  "isCommentingRestricted": false,
                  "commentCount": 28,
                  "isRestricted": false,
                  "user": {
                    "userId": "91000014",
                    "name": "Fixture Name 14",
                    "iconUrl": "https://example.invalid/resource-25"
                  },
                  "creatorId": "fixture-creator-14",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-26"
                  },
                  "excerpt": "Fixture text 14"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-29",
                "notifiedDatetime": "2000-01-01T00:43:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-30",
                  "title": "Fixture Title 15",
                  "feeRequired": 90000015,
                  "publishedDatetime": "2000-01-01T00:44:00+00:00",
                  "updatedDatetime": "2000-01-01T00:45:00+00:00",
                  "tags": [],
                  "isLiked": false,
                  "likeCount": 29,
                  "isCommentingRestricted": false,
                  "commentCount": 30,
                  "isRestricted": true,
                  "user": {
                    "userId": "91000015",
                    "name": "Fixture Name 15",
                    "iconUrl": "https://example.invalid/resource-27"
                  },
                  "creatorId": "fixture-creator-15",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-28"
                  },
                  "excerpt": "Fixture text 15"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-31",
                "notifiedDatetime": "2000-01-01T00:46:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-32",
                  "title": "Fixture Title 16",
                  "feeRequired": 90000016,
                  "publishedDatetime": "2000-01-01T00:47:00+00:00",
                  "updatedDatetime": "2000-01-01T00:48:00+00:00",
                  "tags": [
                    "Fixture Tag 41",
                    "Fixture Tag 42",
                    "Fixture Tag 43",
                    "Fixture Tag 44",
                    "Fixture Tag 45"
                  ],
                  "isLiked": false,
                  "likeCount": 31,
                  "isCommentingRestricted": false,
                  "commentCount": 32,
                  "isRestricted": true,
                  "user": {
                    "userId": "91000016",
                    "name": "Fixture Name 16",
                    "iconUrl": "https://example.invalid/resource-29"
                  },
                  "creatorId": "fixture-creator-16",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-30"
                  },
                  "excerpt": "Fixture text 16"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-33",
                "notifiedDatetime": "2000-01-01T00:49:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-34",
                  "title": "Fixture Title 17",
                  "feeRequired": 90000017,
                  "publishedDatetime": "2000-01-01T00:50:00+00:00",
                  "updatedDatetime": "2000-01-01T00:51:00+00:00",
                  "tags": [
                    "Fixture Tag 46",
                    "Fixture Tag 47",
                    "Fixture Tag 48",
                    "Fixture Tag 49",
                    "Fixture Tag 50",
                    "Fixture Tag 51"
                  ],
                  "isLiked": false,
                  "likeCount": 33,
                  "isCommentingRestricted": true,
                  "commentCount": 34,
                  "isRestricted": false,
                  "user": {
                    "userId": "91000017",
                    "name": "Fixture Name 17",
                    "iconUrl": "https://example.invalid/resource-31"
                  },
                  "creatorId": "fixture-creator-17",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-32"
                  },
                  "excerpt": "Fixture text 17"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-35",
                "notifiedDatetime": "2000-01-01T00:52:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-36",
                  "title": "Fixture Title 18",
                  "feeRequired": 90000018,
                  "publishedDatetime": "2000-01-01T00:53:00+00:00",
                  "updatedDatetime": "2000-01-01T00:54:00+00:00",
                  "tags": [
                    "Fixture Tag 52",
                    "Fixture Tag 53",
                    "Fixture Tag 54",
                    "Fixture Tag 55",
                    "Fixture Tag 56"
                  ],
                  "isLiked": false,
                  "likeCount": 35,
                  "isCommentingRestricted": false,
                  "commentCount": 36,
                  "isRestricted": true,
                  "user": {
                    "userId": "91000018",
                    "name": "Fixture Name 18",
                    "iconUrl": "https://example.invalid/resource-33"
                  },
                  "creatorId": "fixture-creator-18",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-34"
                  },
                  "excerpt": "Fixture text 18"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-37",
                "notifiedDatetime": "2000-01-01T00:55:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-38",
                  "title": "Fixture Title 19",
                  "feeRequired": 90000019,
                  "publishedDatetime": "2000-01-01T00:56:00+00:00",
                  "updatedDatetime": "2000-01-01T00:57:00+00:00",
                  "tags": [
                    "Fixture Tag 57",
                    "Fixture Tag 58",
                    "Fixture Tag 59",
                    "Fixture Tag 60"
                  ],
                  "isLiked": false,
                  "likeCount": 37,
                  "isCommentingRestricted": false,
                  "commentCount": 38,
                  "isRestricted": false,
                  "user": {
                    "userId": "91000019",
                    "name": "Fixture Name 19",
                    "iconUrl": "https://example.invalid/resource-35"
                  },
                  "creatorId": "fixture-creator-19",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-36"
                  },
                  "excerpt": "Fixture text 19"
                },
                "isUnread": false
              },
              {
                "id": "fixture-bell-39",
                "notifiedDatetime": "2000-01-01T00:58:00+00:00",
                "type": "on_post_published",
                "post": {
                  "id": "fixture-bell-40",
                  "title": "Fixture Title 20",
                  "feeRequired": 90000020,
                  "publishedDatetime": "2000-01-01T00:59:00+00:00",
                  "updatedDatetime": "2000-01-01T00:00:00+00:00",
                  "tags": [
                    "Fixture Tag 61"
                  ],
                  "isLiked": false,
                  "likeCount": 39,
                  "isCommentingRestricted": false,
                  "commentCount": 40,
                  "isRestricted": true,
                  "user": {
                    "userId": "91000020",
                    "name": "Fixture Name 20",
                    "iconUrl": "https://example.invalid/resource-37"
                  },
                  "creatorId": "fixture-creator-20",
                  "hasAdultContent": false,
                  "cover": {
                    "type": "cover_image",
                    "url": "https://example.invalid/resource-38"
                  },
                  "excerpt": "Fixture text 20"
                },
                "isUnread": false
              }
            ],
            "nextUrl": "https://api.fanbox.cc/bell.list?page=2"
          }
        }
        """.trimIndent()

    /**
     * Handcrafted newsletter.list response for an unavailable account-specific variation.
     *
     * This synthetic fixture is not captured from an actual response.
     */
    val handcraftedNewsletterList =
        """
        {
          "body": [
            {
              "body": "Handcrafted newsletter body",
              "createdAt": "2000-02-01T00:00:00+00:00",
              "creator": {
                "creatorId": "handcrafted-newsletter-creator",
                "user": {
                  "iconUrl": "https://example.invalid/handcrafted-newsletter-user.png",
                  "name": "Handcrafted Newsletter Creator",
                  "userId": "95000001"
                }
              },
              "id": "handcrafted-newsletter-1",
              "isRead": false
            }
          ]
        }
        """.trimIndent()

    /**
     * Handcrafted bell.list response for the unavailable post_comment variation.
     *
     * This synthetic fixture is not captured from an actual response.
     */
    val handcraftedBellPostComment =
        """
        {
          "body": {
            "items": [
              {
                "count": null,
                "creatorId": "handcrafted-comment-creator",
                "creatorUserId": null,
                "id": "handcrafted-comment-notification-1",
                "isRootComment": true,
                "isUnread": false,
                "notifiedDatetime": "2000-02-02T00:00:00+00:00",
                "post": null,
                "postCommentBody": "Handcrafted comment body",
                "postId": "95000002",
                "postTitle": "Handcrafted comment post",
                "type": "post_comment",
                "userName": "Handcrafted Commenter",
                "userProfileImg": "https://example.invalid/handcrafted-commenter.png"
              }
            ],
            "nextUrl": null
          }
        }
        """.trimIndent()

    /**
     * Handcrafted bell.list response for the unavailable post_comment_like variation.
     *
     * This synthetic fixture is not captured from an actual response.
     */
    val handcraftedBellPostCommentLike =
        """
        {
          "body": {
            "items": [
              {
                "count": 3,
                "creatorId": "handcrafted-like-creator",
                "creatorUserId": null,
                "id": "handcrafted-like-notification-1",
                "isRootComment": null,
                "isUnread": false,
                "notifiedDatetime": "2000-02-03T00:00:00+00:00",
                "post": null,
                "postCommentBody": "Handcrafted liked comment body",
                "postId": "95000003",
                "postTitle": null,
                "type": "post_comment_like",
                "userName": null,
                "userProfileImg": null
              }
            ],
            "nextUrl": null
          }
        }
        """.trimIndent()
}
