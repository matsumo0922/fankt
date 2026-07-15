package me.matsumo.fankt.fanbox.fixture

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
}
