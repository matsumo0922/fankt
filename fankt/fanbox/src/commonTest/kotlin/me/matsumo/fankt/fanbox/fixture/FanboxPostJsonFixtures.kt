package me.matsumo.fankt.fanbox.fixture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal object FanboxPostJsonFixtures {
    val postInfoArticleA =
        """
        {
          "body": {
            "post": {
              "body": {
                "text": null,
                "blocks": [
                  {
                    "type": "p",
                    "text": "",
                    "imageId": null,
                    "fileId": null,
                    "urlEmbedId": null
                  },
                  {
                    "type": "image",
                    "text": null,
                    "imageId": "fixture-image-a-1",
                    "fileId": null,
                    "urlEmbedId": null
                  },
                  {
                    "type": "p",
                    "text": "",
                    "imageId": null,
                    "fileId": null,
                    "urlEmbedId": null
                  },
                  {
                    "type": "file",
                    "text": null,
                    "imageId": null,
                    "fileId": "fixture-file-a-1",
                    "urlEmbedId": null
                  },
                  {
                    "type": "p",
                    "text": "Fixture A Text 1",
                    "imageId": null,
                    "fileId": null,
                    "urlEmbedId": null
                  },
                  {
                    "type": "p",
                    "text": "",
                    "imageId": null,
                    "fileId": null,
                    "urlEmbedId": null
                  },
                  {
                    "type": "p",
                    "text": "Fixture A Text 2",
                    "imageId": null,
                    "fileId": null,
                    "urlEmbedId": null
                  },
                  {
                    "type": "p",
                    "text": "Fixture A Text 3",
                    "imageId": null,
                    "fileId": null,
                    "urlEmbedId": null
                  },
                  {
                    "type": "p",
                    "text": "Fixture A Text 4",
                    "imageId": null,
                    "fileId": null,
                    "urlEmbedId": null
                  }
                ],
                "fileMap": {
                  "fixture-file-a-1": {
                    "extension": "pdf",
                    "id": "fixture-file-a-1",
                    "name": "fixture-a-file-1.pdf",
                    "size": 4096,
                    "url": "https://example.invalid/a-file-1.pdf"
                  }
                },
                "imageMap": {
                  "fixture-image-a-1": {
                    "extension": "jpg",
                    "height": 801,
                    "id": "fixture-image-a-1",
                    "originalUrl": "https://example.invalid/a-image-1-original.jpg",
                    "thumbnailUrl": "https://example.invalid/a-image-1-thumbnail.jpg",
                    "width": 1201
                  }
                },
                "urlEmbedMap": {},
                "images": [],
                "files": [],
                "futureApiField": {
                  "fixtureNested": true
                }
              },
              "commentCount": 2,
              "creatorId": "fixture-creator-article-a",
              "excerpt": "Fixture article A excerpt",
              "feeRequired": 0,
              "hasAdultContent": false,
              "id": "10000002",
              "imageForShare": "https://example.invalid/article-a-share.jpg",
              "isLiked": false,
              "isRestricted": false,
              "likeCount": 4,
              "nextPost": {
                "id": "11000001",
                "publishedDatetime": "2000-01-02T00:00:00+00:00",
                "title": "Fixture A Next Post"
              },
              "prevPost": {
                "id": "11000002",
                "publishedDatetime": "2000-01-03T00:00:00+00:00",
                "title": "Fixture A Prev Post"
              },
              "publishedDatetime": "2000-03-01T00:00:00+00:00",
              "tags": [
                "fixture-a-tag-1",
                "fixture-a-tag-2",
                "fixture-a-tag-3",
                "fixture-a-tag-4"
              ],
              "title": "Fixture Article A",
              "type": "article",
              "updatedDatetime": "2000-03-03T00:00:00+00:00",
              "coverImageUrl": "https://example.invalid/article-a-cover.jpg",
              "user": {
                "iconUrl": "https://example.invalid/a-user-icon.png",
                "name": "Fixture A User",
                "userId": "91000001"
              }
            }
          },
          "futureApiField": "fixture-top-level"
        }
        """.trimIndent()

    val postInfoArticleB =
        """
        {
          "body": {
            "post": {
              "body": {
                "text": null,
                "blocks": [
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"header","text":"Fixture B Header 1","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"Fixture B Text 1","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"Fixture B Text 2","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"image","text":null,"imageId":"fixture-image-b-1","fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"url_embed","text":null,"imageId":null,"fileId":null,"urlEmbedId":"fixture-url-b-1"},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"Fixture B Text 3","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"header","text":"Fixture B Header 2","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"url_embed","text":null,"imageId":null,"fileId":null,"urlEmbedId":"fixture-url-b-4"},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"url_embed","text":null,"imageId":null,"fileId":null,"urlEmbedId":"fixture-url-b-5"},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"p","text":"Fixture B Text 4","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"url_embed","text":null,"imageId":null,"fileId":null,"urlEmbedId":"fixture-url-b-3"},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null},
                  {"type":"url_embed","text":null,"imageId":null,"fileId":null,"urlEmbedId":"fixture-url-b-2"},
                  {"type":"p","text":"","imageId":null,"fileId":null,"urlEmbedId":null}
                ],
                "fileMap": {},
                "imageMap": {
                  "fixture-image-b-1": {
                    "extension": "jpg",
                    "height": 801,
                    "id": "fixture-image-b-1",
                    "originalUrl": "https://example.invalid/b-image-1-original.jpg",
                    "thumbnailUrl": "https://example.invalid/b-image-1-thumbnail.jpg",
                    "width": 1201
                  }
                },
                "urlEmbedMap": {
                  "fixture-url-b-1": {
                    "id": "fixture-url-b-1",
                    "type": "fanbox.post",
                    "html": null,
                    "postInfo": {
                      "commentCount": 1,
                      "cover": {"type":"cover_image","url":"https://example.invalid/b-embedded-1-cover.jpg"},
                      "creatorId": "fixture-creator-b-embedded-1",
                      "excerpt": "Fixture B embedded post 1 excerpt",
                      "feeRequired": 0,
                      "hasAdultContent": false,
                      "id": "13000001",
                      "isLiked": false,
                      "isRestricted": false,
                      "likeCount": 2,
                      "publishedDatetime": "2000-02-01T00:00:00+00:00",
                      "tags": ["fixture-embedded-tag-1"],
                      "title": "Fixture B Embedded Post 1",
                      "updatedDatetime": "2000-02-02T00:00:00+00:00",
                      "user": {
                        "iconUrl": "https://example.invalid/b-embedded-1-user.png",
                        "name": "Fixture B Embedded User 1",
                        "userId": "92000001"
                      }
                    }
                  },
                  "fixture-url-b-2": {"id":"fixture-url-b-2","type":"html","html":"<div>Fixture B Link 2</div>","postInfo":null},
                  "fixture-url-b-3": {"id":"fixture-url-b-3","type":"html","html":"<div>Fixture B Link 3</div>","postInfo":null},
                  "fixture-url-b-4": {"id":"fixture-url-b-4","type":"html","html":"<div>Fixture B Link 4</div>","postInfo":null},
                  "fixture-url-b-5": {"id":"fixture-url-b-5","type":"html","html":"<div>Fixture B Link 5</div>","postInfo":null}
                },
                "images": [],
                "files": []
              },
              "commentCount": 3,
              "creatorId": "fixture-creator-article-b",
              "excerpt": "Fixture article B excerpt",
              "feeRequired": 0,
              "hasAdultContent": false,
              "id": "10000003",
              "imageForShare": "https://example.invalid/article-b-share.jpg",
              "isLiked": false,
              "isRestricted": false,
              "likeCount": 5,
              "nextPost": {"id":"12000001","publishedDatetime":"2000-01-02T00:00:00+00:00","title":"Fixture B Next Post"},
              "prevPost": {"id":"12000002","publishedDatetime":"2000-01-03T00:00:00+00:00","title":"Fixture B Prev Post"},
              "publishedDatetime": "2000-03-02T00:00:00+00:00",
              "tags": [],
              "title": "Fixture Article B",
              "type": "article",
              "updatedDatetime": "2000-03-03T00:00:00+00:00",
              "coverImageUrl": "https://example.invalid/article-b-cover.jpg",
              "user": {
                "iconUrl": "https://example.invalid/b-user-icon.png",
                "name": "Fixture B User",
                "userId": "91000002"
              }
            }
          }
        }
        """.trimIndent()

    /**
     * Issue #29 actual-response-derived fixture. The article envelope and embedded FANBOX post
     * come from the existing anonymized response-derived Article B fixture. The four URL embed
     * fragments preserve the observed type-specific field presence and scalar/object shapes;
     * identity, URL, and free-form values are whole-value placeholders. The fragments are composed
     * into one envelope for coverage and are not claimed to be one complete original response.
     */
    val postInfoArticleUrlEmbeds by lazy {
        val fanboxPostInfo = responseDerivedEmbeddedFanboxPostInfo()
        articleUrlEmbedPostInfo(
            """
            {
              "fixture-url-default": {
                "id": "fixture-url-default",
                "type": "default",
                "url": "https://example.invalid/plain-link"
              },
              "fixture-url-html": {
                "id": "fixture-url-html",
                "type": "html",
                "html": "<div>Fixture HTML embed</div>"
              },
              "fixture-url-html-card": {
                "id": "fixture-url-html-card",
                "type": "html.card",
                "html": "<article>Fixture HTML card embed</article>"
              },
              "fixture-url-fanbox-post": {
                "id": "fixture-url-fanbox-post",
                "type": "fanbox.post",
                "postInfo": $fanboxPostInfo
              }
            }
            """.trimIndent(),
        )
    }

    /** Synthetic fixture for forward-compatible unknown URL embed metadata passthrough. */
    val postInfoArticleUnknownUrlEmbed by lazy {
        val fanboxPostInfo = responseDerivedEmbeddedFanboxPostInfo()
        articleUrlEmbedPostInfo(
            """
            {
              "fixture-url-unknown": {
                "id": "fixture-url-unknown",
                "type": "future.embed",
                "url": "https://example.invalid/future-link",
                "html": "<aside>Fixture future embed</aside>",
                "postInfo": $fanboxPostInfo
              }
            }
            """.trimIndent(),
        )
    }

    /**
     * Issue #30 actual-response-derived fragments. The header, bold-plus-link paragraph, and empty
     * paragraph preserve the observed field presence and scalar/list/object shapes; text and URL
     * values are whole-value placeholders. The fragments are composed into the existing anonymized
     * Article B envelope and are not claimed to be one complete original response.
     */
    val postInfoArticleTextMetadata by lazy {
        articleTextBlockPostInfo(
            """
            [
              {"type":"header","text":"Fixture Heading"},
              {"type":"p","text":"Fixture bold link","styles":[{"type":"bold","offset":0,"length":7}],"links":[{"offset":8,"length":4,"url":"https://example.invalid/inline"}]},
              {"type":"p","text":""}
            ]
            """.trimIndent(),
        )
    }

    /** Synthetic contract probes for unknown style passthrough and incomplete-span omission. */
    val postInfoArticleSyntheticTextSpans by lazy {
        articleTextBlockPostInfo(
            """
            [
              {"type":"p","text":"Fixture future style","styles":[{"type":"future-style","offset":8,"length":4}]},
              {"type":"p","text":"Fixture incomplete spans","styles":[{"type":"bold","offset":0},{"type":null,"offset":0,"length":7}],"links":[{"offset":8,"length":4},{"offset":null,"length":4,"url":"https://example.invalid/incomplete"}]}
            ]
            """.trimIndent(),
        )
    }

    /** Synthetic schema-drift fixture: only the decorative span containers have wrong shapes. */
    val postInfoArticleMalformedTextSpans by lazy {
        articleTextBlockPostInfo(
            """[{"type":"header","text":"Fixture degraded heading","styles":[{"type":"bold","offset":"unexpected","length":7}],"links":[{"offset":0,"length":{"unexpected":true},"url":"https://example.invalid/malformed"}]}]""",
        )
    }

    /** Synthetic known-block failure fixture: span drift must not hide malformed text. */
    val postInfoArticleMalformedTextAndSpans by lazy {
        articleTextBlockPostInfo(
            """[{"type":"p","text":{"unexpected":true},"styles":[{"type":"bold","offset":"unexpected","length":7}],"links":[]}]""",
        )
    }

    val postInfoText =
        """
        {
          "body": {
            "post": {
              "body": null,
              "commentCount": 0,
              "creatorId": "fixture-creator-text",
              "excerpt": "Fixture text excerpt",
              "feeRequired": 0,
              "hasAdultContent": false,
              "id": "10000001",
              "imageForShare": "https://example.invalid/text-share.jpg",
              "isLiked": false,
              "isRestricted": false,
              "likeCount": 0,
              "nextPost": null,
              "prevPost": null,
              "publishedDatetime": "2026-07-13T00:00:00+00:00",
              "tags": [],
              "title": "Fixture text post",
              "type": "text",
              "updatedDatetime": "2026-07-13T00:00:00+00:00",
              "coverImageUrl": null,
              "user": null
            }
          }
        }
        """.trimIndent()

    /**
     * Issue #27 hybrid fixture. The envelope and common post fields come from the anonymized,
     * response-derived image fixture. The text type and body placement are composed from the
     * documented legacy shape. The complete production text response schema remains unverified.
     */
    val postInfoTextHybrid by lazy {
        hybridLegacyPostInfo(
            type = "text",
            bodyJson =
            """{"text":"Fixture legacy text","video":{"serviceProvider":"youtube","videoId":"ignored-video"},"html":"<p>Ignored HTML</p>"}""",
        )
    }

    /**
     * Issue #27 hybrid fixture. The envelope and common post fields are response-derived; the video
     * type and nested video placement are composed from the documented legacy shape. The complete
     * production video response schema remains unverified.
     */
    val postInfoVideo by lazy {
        hybridLegacyPostInfo(
            type = "video",
            bodyJson =
            """{"video":{"serviceProvider":"youtube","videoId":"fixture-video-1","contentId":"fixture-content-ignored"}}""",
        )
    }

    /** Hybrid issue #27 fixture for the legacy contentId spelling; production schema is unverified. */
    val postInfoVideoContentId by lazy {
        hybridLegacyPostInfo(
            type = "video",
            bodyJson = """{"video":{"serviceProvider":"vimeo","contentId":"fixture-video-2"}}""",
        )
    }

    /**
     * Issue #27 hybrid fixture. The envelope and common post fields are response-derived. The HTML
     * scalar shape is also present in the response-derived article urlEmbedMap; only the entry type
     * and body placement are composed. The complete production entry response schema is unverified.
     */
    val postInfoEntry by lazy {
        hybridLegacyPostInfo(type = "entry", bodyJson = """{"html":"<p>Fixture legacy HTML</p>"}""")
    }

    val postInfoMalformedText by lazy {
        hybridLegacyPostInfo(type = "text", bodyJson = """{"text":{"unexpected":true}}""")
    }

    val postInfoMalformedVideo by lazy {
        hybridLegacyPostInfo(type = "video", bodyJson = """{"video":"unexpected"}""")
    }

    val postInfoMalformedEntry by lazy {
        hybridLegacyPostInfo(type = "entry", bodyJson = """{"html":{"unexpected":true}}""")
    }

    val postInfoNullVideo by lazy {
        hybridLegacyPostInfo(type = "video", bodyJson = "null")
    }

    /** Synthetic fixture for the fail-safe unknown-type branch; it is not response-derived. */
    val postInfoUnknownType = syntheticUnsupportedPostInfo(
        type = "future-post-type",
        bodyJson = """{"futureField":"fixture-future-value"}""",
    )

    /**
     * Handcrafted synthetic fixture approved for issue #28. It verifies internal embed decoding,
     * reference resolution, URL restoration, and raw fallback only. It is not derived from an
     * actual response and does not prove the production embedMap schema.
     */
    val postInfoArticleEmbeds = syntheticUnsupportedPostInfo(
        type = "article",
        bodyJson = """
            {
              "text": null,
              "blocks": [
                {"type":"p","text":"Fixture Before","embedId":"fixture-embed-twitter"},
                {"type":"embed","embedId":"fixture-embed-twitter"},
                {"type":"embed","embedId":"fixture-embed-youtube"},
                {"type":"embed","embedId":"fixture-embed-vimeo"},
                {"type":"embed","embedId":"fixture-embed-soundcloud"},
                {"type":"embed","embedId":"fixture-embed-google-forms"},
                {"type":"embed","embedId":"fixture-embed-fanbox"},
                {"type":"embed","embedId":"fixture-embed-unknown"},
                {"type":"future-block","text":{"malformed":true},"futureField":"fixture-future-block-value"},
                {"type":"embed","embedId":"fixture-embed-missing-provider"},
                {"type":"embed","embedId":"fixture-embed-missing"},
                {"type":"image","imageId":"fixture-image-missing"},
                {"type":"file","fileId":"fixture-file-missing"},
                {"type":"url_embed","urlEmbedId":"fixture-url-missing"},
                {"type":"p","text":"Fixture After"}
              ],
              "embedMap": {
                "fixture-embed-twitter": {
                  "id":"fixture-embed-twitter",
                  "serviceProvider":"twitter",
                  "contentId":"twitter-content"
                },
                "fixture-embed-youtube": {
                  "serviceProvider":"youtube",
                  "contentId":"youtube-content-ignored",
                  "videoId":"youtube-video"
                },
                "fixture-embed-vimeo": {
                  "id":"fixture-embed-vimeo",
                  "serviceProvider":"vimeo",
                  "contentId":"vimeo-content"
                },
                "fixture-embed-soundcloud": {
                  "id":"fixture-embed-soundcloud",
                  "serviceProvider":"soundcloud",
                  "contentId":"soundcloud-content"
                },
                "fixture-embed-google-forms": {
                  "id":"fixture-embed-google-forms",
                  "serviceProvider":"google_forms",
                  "contentId":"google-forms-content"
                },
                "fixture-embed-fanbox": {
                  "id":"fixture-embed-fanbox",
                  "serviceProvider":"fanbox",
                  "contentId":"fanbox-content"
                },
                "fixture-embed-unknown": {
                  "id":"fixture-embed-unknown",
                  "serviceProvider":"future-provider",
                  "contentId":"future-content"
                },
                "fixture-embed-missing-provider": {
                  "id":"fixture-embed-missing-provider",
                  "contentId":"missing-provider-content"
                }
              },
              "fileMap": {},
              "imageMap": {},
              "urlEmbedMap": {},
              "images": [],
              "files": []
            }
        """.trimIndent(),
    )

    /** Synthetic issue #28 fixture for the known-block SchemaMismatch production path. */
    val postInfoMalformedArticleBlock = syntheticUnsupportedPostInfo(
        type = "article",
        bodyJson = """
            {
              "text": null,
              "blocks": [{"type":"image","imageId":{"malformed":true}}],
              "embedMap": {},
              "fileMap": {},
              "imageMap": {},
              "urlEmbedMap": {},
              "images": [],
              "files": []
            }
        """.trimIndent(),
    )

    val postInfoImage =
        """
        {
          "body": {
            "post": {
              "body": {
                "text": "",
                "images": [
                  {
                    "extension": "jpeg",
                    "height": 2720,
                    "id": "fixture-image-1",
                    "originalUrl": "https://example.invalid/resource-1",
                    "thumbnailUrl": "https://example.invalid/resource-2",
                    "width": 3687
                  }
                ]
              },
              "commentCount": 0,
              "creatorId": "fixture-creator-1",
              "excerpt": "",
              "feeRequired": 0,
              "hasAdultContent": true,
              "id": "10000001",
              "imageForShare": "https://example.invalid/resource-3",
              "isLiked": false,
              "isRestricted": false,
              "likeCount": 16,
              "nextPost": {
                "id": "10000002",
                "publishedDatetime": "2000-01-01T00:02:00+00:00",
                "title": "Fixture Post Title 1"
              },
              "prevPost": {
                "id": "10000003",
                "publishedDatetime": "2000-01-01T00:00:00+00:00",
                "title": "Fixture Post Title 2"
              },
              "publishedDatetime": "2000-01-01T00:01:00+00:00",
              "tags": [
                "Fixture Tag 1"
              ],
              "title": "Fixture Post Title 3",
              "type": "image",
              "updatedDatetime": "2000-01-01T00:03:00+00:00",
              "coverImageUrl": "https://example.invalid/resource-3",
              "user": {
                "iconUrl": "https://example.invalid/resource-4",
                "name": "Fixture User Name 1",
                "userId": "90000001"
              }
            }
          }
        }
        """.trimIndent()

    /**
     * Synthetic branch-selection fixture. The payload is image-shaped while post.type is file, so
     * it verifies that non-empty payload fields do not override the explicit type.
     */
    val postInfoFileTypeWithImagePayloadFields = postInfoImage.replaceFirst(
        oldValue = "\"type\": \"image\"",
        newValue = "\"type\": \"file\"",
    )

    /** Synthetic malformed-known-body fixture for the public SchemaMismatch contract. */
    val postInfoMalformedImage = postInfoImage.replaceFirst(
        oldValue = "\"extension\": \"jpeg\"",
        newValue = "\"unexpectedExtension\": \"jpeg\"",
    )

    /** Synthetic known-type null-body fixture for the public SchemaMismatch contract. */
    val postInfoNullArticle = postInfoText.replaceFirst(
        oldValue = "\"type\": \"text\"",
        newValue = "\"type\": \"article\"",
    )

    val postInfoFile =
        """
        {
          "body": {
            "post": {
              "body": {
                "text": "Fixture Post Text 1",
                "files": [
                  {
                    "extension": "mp3",
                    "id": "fixture-file-1",
                    "name": "fixture-file-name-1.mp3",
                    "size": 2193140,
                    "url": "https://example.invalid/resource-1"
                  },
                  {
                    "extension": "mp3",
                    "id": "fixture-file-2",
                    "name": "fixture-file-name-2.mp3",
                    "size": 1921049,
                    "url": "https://example.invalid/resource-2"
                  }
                ]
              },
              "commentCount": 0,
              "creatorId": "fixture-creator-1",
              "excerpt": "Fixture Excerpt 1",
              "feeRequired": 0,
              "hasAdultContent": false,
              "id": "10000001",
              "imageForShare": "https://example.invalid/resource-3",
              "isLiked": false,
              "isRestricted": false,
              "likeCount": 1,
              "nextPost": {
                "id": "10000002",
                "publishedDatetime": "2000-01-01T00:02:00+00:00",
                "title": "Fixture Post Title 1"
              },
              "prevPost": {
                "id": "10000003",
                "publishedDatetime": "2000-01-01T00:00:00+00:00",
                "title": "Fixture Post Title 2"
              },
              "publishedDatetime": "2000-01-01T00:01:00+00:00",
              "tags": [
                "Fixture Tag 1",
                "Fixture Tag 2",
                "Fixture Tag 3",
                "Fixture Tag 4",
                "Fixture Tag 5"
              ],
              "title": "Fixture Post Title 3",
              "type": "file",
              "updatedDatetime": "2000-01-01T00:01:00+00:00",
              "coverImageUrl": null,
              "user": {
                "iconUrl": "https://example.invalid/resource-4",
                "name": "Fixture User Name 1",
                "userId": "90000001"
              }
            }
          }
        }
        """.trimIndent()

    val postListHomeNormal =
        """
        {
          "body": {
            "items": [
              {
                "commentCount": 1,
                "cover": {
                  "type": "cover_image",
                  "url": "https://example.invalid/resource-1"
                },
                "creatorId": "fixture-creator-1",
                "excerpt": "",
                "feeRequired": 500,
                "hasAdultContent": false,
                "id": "10000001",
                "isLiked": false,
                "isRestricted": true,
                "likeCount": 0,
                "publishedDatetime": "2000-01-01T00:01:00+00:00",
                "tags": [
                  "Fixture Tag 1",
                  "Fixture Tag 2",
                  "Fixture Tag 3"
                ],
                "title": "Fixture Post Title 1",
                "updatedDatetime": "2000-01-01T00:01:00+00:00",
                "user": {
                  "iconUrl": "https://example.invalid/resource-2",
                  "name": "Fixture User Name 1",
                  "userId": "90000001"
                }
              },
              {
                "commentCount": 0,
                "cover": null,
                "creatorId": "fixture-creator-1",
                "excerpt": "Fixture Excerpt 1",
                "feeRequired": 0,
                "hasAdultContent": false,
                "id": "10000002",
                "isLiked": false,
                "isRestricted": false,
                "likeCount": 3,
                "publishedDatetime": "2000-01-01T00:00:00+00:00",
                "tags": [],
                "title": "Fixture Post Title 2",
                "updatedDatetime": "2000-01-01T00:00:00+00:00",
                "user": {
                  "iconUrl": "https://example.invalid/resource-2",
                  "name": "Fixture User Name 1",
                  "userId": "90000001"
                }
              }
            ],
            "nextUrl": "https://example.invalid/endpoint?maxPublishedDatetime=2000-01-01T00%3A00%3A00%2B00%3A00&maxId=10000003&limit=2"
          }
        }
        """.trimIndent()

    val postListHomeEmpty =
        """
        {
          "body": {
            "items": [],
            "nextUrl": null
          }
        }
        """.trimIndent()

    val postListCreatorNormal =
        """
        {
          "body": [
            {
              "commentCount": 0,
              "cover": null,
              "creatorId": "fixture-creator-1",
              "excerpt": "Fixture Excerpt 1",
              "feeRequired": 0,
              "hasAdultContent": false,
              "id": "10000001",
              "isLiked": false,
              "isRestricted": false,
              "likeCount": 0,
              "publishedDatetime": "2000-01-01T00:01:00+00:00",
              "tags": [
                "Fixture Tag 1",
                "Fixture Tag 2",
                "Fixture Tag 3",
                "Fixture Tag 4",
                "Fixture Tag 5"
              ],
              "title": "Fixture Post Title 1",
              "updatedDatetime": "2000-01-01T00:01:00+00:00",
              "user": {
                "iconUrl": "https://example.invalid/resource-1",
                "name": "Fixture User Name 1",
                "userId": "90000001"
              }
            },
            {
              "commentCount": 0,
              "cover": null,
              "creatorId": "fixture-creator-1",
              "excerpt": "Fixture Excerpt 2",
              "feeRequired": 0,
              "hasAdultContent": false,
              "id": "10000002",
              "isLiked": false,
              "isRestricted": false,
              "likeCount": 1,
              "publishedDatetime": "2000-01-01T00:00:00+00:00",
              "tags": [
                "Fixture Tag 5",
                "Fixture Tag 4",
                "Fixture Tag 3",
                "Fixture Tag 2",
                "Fixture Tag 1"
              ],
              "title": "Fixture Post Title 2",
              "updatedDatetime": "2000-01-01T00:00:00+00:00",
              "user": {
                "iconUrl": "https://example.invalid/resource-1",
                "name": "Fixture User Name 1",
                "userId": "90000001"
              }
            }
          ]
        }
        """.trimIndent()

    val postListCreatorEmpty =
        """
        {
          "body": []
        }
        """.trimIndent()

    val paginateCreatorNormal =
        """
        {
          "body": [
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A00%3A00%2B00%3A00&firstId=10000001&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A01%3A00%2B00%3A00&firstId=10000002&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A02%3A00%2B00%3A00&firstId=10000003&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A03%3A00%2B00%3A00&firstId=10000004&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A04%3A00%2B00%3A00&firstId=10000005&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A05%3A00%2B00%3A00&firstId=10000006&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A06%3A00%2B00%3A00&firstId=10000007&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A07%3A00%2B00%3A00&firstId=10000008&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A08%3A00%2B00%3A00&firstId=10000009&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A09%3A00%2B00%3A00&firstId=10000010&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A10%3A00%2B00%3A00&firstId=10000011&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A11%3A00%2B00%3A00&firstId=10000012&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A12%3A00%2B00%3A00&firstId=10000013&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A13%3A00%2B00%3A00&firstId=10000014&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A14%3A00%2B00%3A00&firstId=10000015&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A15%3A00%2B00%3A00&firstId=10000016&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A16%3A00%2B00%3A00&firstId=10000017&sort=fixture-token&limit=10",
            "https://example.invalid/endpoint?creatorId=fixture-creator-1&firstPublishedDatetime=2000-01-01T00%3A17%3A00%2B00%3A00&firstId=10000018&sort=fixture-token&limit=10"
          ]
        }
        """.trimIndent()

    val paginateCreatorEmpty =
        """
        {
          "body": []
        }
        """.trimIndent()

    val postCommentsFlat =
        """
        {
          "body": {
            "viewMode": "OPEN",
            "commentList": {
              "items": [
                {
                  "body": "Fixture Comment Body 1",
                  "createdDatetime": "2000-01-01T00:00:00+00:00",
                  "id": "20000001",
                  "isLiked": false,
                  "isOwn": false,
                  "likeCount": 1,
                  "parentCommentId": "0",
                  "rootCommentId": "0",
                  "user": {
                    "iconUrl": "https://example.invalid/user-1.png",
                    "name": "Fixture Comment User 1",
                    "userId": "90000001"
                  },
                  "replies": []
                }
              ],
              "nextUrl": null
            }
          }
        }
        """.trimIndent()

    /** Handcrafted synthetic fixture for nested reply ordering; it is not derived from an actual response. */
    val handcraftedPostCommentsNestedReplies =
        """
        {
          "body": {
            "viewMode": "OPEN",
            "commentList": {
              "items": [
                {
                  "body": "Handcrafted fixture root comment content",
                  "createdDatetime": "2000-01-01T00:00:00+00:00",
                  "id": "handcrafted-root-comment",
                  "isLiked": false,
                  "isOwn": false,
                  "likeCount": 0,
                  "parentCommentId": "0",
                  "rootCommentId": "0",
                  "user": null,
                  "replies": [
                    {
                      "body": "Handcrafted fixture later reply content",
                      "createdDatetime": "2000-01-01T00:02:00+00:00",
                      "id": "handcrafted-later-reply",
                      "isLiked": true,
                      "isOwn": false,
                      "likeCount": 2,
                      "parentCommentId": "handcrafted-root-comment",
                      "rootCommentId": "handcrafted-root-comment",
                      "user": null,
                      "replies": []
                    },
                    {
                      "body": "Handcrafted fixture earlier reply content",
                      "createdDatetime": "2000-01-01T00:01:00+00:00",
                      "id": "handcrafted-earlier-reply",
                      "isLiked": false,
                      "isOwn": true,
                      "likeCount": 1,
                      "parentCommentId": "handcrafted-root-comment",
                      "rootCommentId": "handcrafted-root-comment",
                      "user": null,
                      "replies": []
                    }
                  ]
                }
              ],
              "nextUrl": null
            }
          }
        }
        """.trimIndent()

    /** Handcrafted synthetic fixture for nextUrl offset extraction; it is not derived from an actual response. */
    val handcraftedPostCommentsNextUrlOffset =
        """
        {
          "body": {
            "viewMode": "OPEN",
            "commentList": {
              "items": [],
              "nextUrl": "https://example.invalid/comments?offset=23"
            }
          }
        }
        """.trimIndent()

    private fun syntheticUnsupportedPostInfo(type: String, bodyJson: String): String {
        return postInfoText
            .replaceFirst(oldValue = "\"body\": null", newValue = "\"body\": $bodyJson")
            .replaceFirst(oldValue = "\"type\": \"text\"", newValue = "\"type\": \"$type\"")
    }

    private fun hybridLegacyPostInfo(type: String, bodyJson: String): String {
        val root = Json.parseToJsonElement(postInfoImage).jsonObject
        val responseBody = root.getValue("body").jsonObject
        val post = responseBody.getValue("post").jsonObject
        val hybridPost = JsonObject(
            post + mapOf(
                "body" to Json.parseToJsonElement(bodyJson),
                "type" to JsonPrimitive(type),
            ),
        )
        return JsonObject(
            root + ("body" to JsonObject(responseBody + ("post" to hybridPost))),
        ).toString()
    }

    private fun articleUrlEmbedPostInfo(urlEmbedMapJson: String): String {
        val root = Json.parseToJsonElement(postInfoArticleB).jsonObject
        val responseBody = root.getValue("body").jsonObject
        val post = responseBody.getValue("post").jsonObject
        val articleBody = post.getValue("body").jsonObject
        val urlEmbedMap = Json.parseToJsonElement(urlEmbedMapJson).jsonObject
        val blocks = JsonArray(
            urlEmbedMap.keys.map { id ->
                Json.parseToJsonElement(
                    """{"type":"url_embed","text":null,"imageId":null,"fileId":null,"urlEmbedId":"$id"}""",
                )
            },
        )
        val fixtureBody = JsonObject(
            articleBody + mapOf(
                "blocks" to blocks,
                "urlEmbedMap" to urlEmbedMap,
            ),
        )
        val fixturePost = JsonObject(post + ("body" to fixtureBody))
        return JsonObject(
            root + ("body" to JsonObject(responseBody + ("post" to fixturePost))),
        ).toString()
    }

    private fun articleTextBlockPostInfo(blocksJson: String): String {
        val root = Json.parseToJsonElement(postInfoArticleB).jsonObject
        val responseBody = root.getValue("body").jsonObject
        val post = responseBody.getValue("post").jsonObject
        val articleBody = post.getValue("body").jsonObject
        val fixtureBody = JsonObject(
            articleBody + ("blocks" to Json.parseToJsonElement(blocksJson)),
        )
        val fixturePost = JsonObject(post + ("body" to fixtureBody))
        return JsonObject(
            root + ("body" to JsonObject(responseBody + ("post" to fixturePost))),
        ).toString()
    }

    private fun responseDerivedEmbeddedFanboxPostInfo() =
        Json.parseToJsonElement(postInfoArticleB).jsonObject
            .getValue("body").jsonObject
            .getValue("post").jsonObject
            .getValue("body").jsonObject
            .getValue("urlEmbedMap").jsonObject
            .getValue("fixture-url-b-1").jsonObject
            .getValue("postInfo")
}
