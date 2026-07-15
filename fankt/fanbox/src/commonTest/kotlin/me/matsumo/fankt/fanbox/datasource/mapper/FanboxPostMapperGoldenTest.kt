package me.matsumo.fankt.fanbox.datasource.mapper

import kotlinx.datetime.Instant
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxCover
import me.matsumo.fankt.fanbox.domain.model.FanboxPost
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxUser
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostItemId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import me.matsumo.fankt.fanbox.fixture.decodeFixture
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxPostMapperGoldenTest {

    @Test
    fun postInfoArticleAWithTextImageFileReferencesAndUnknownFieldsMapsFullObject() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoArticleA),
        )

        val expected = FanboxPostDetail(
            id = FanboxPostId("10000002"),
            title = "Fixture Article A",
            body = FanboxPostDetail.Body.Article(
                blocks = listOf(
                    FanboxPostDetail.Body.Article.Block.Image(
                        item = FanboxPostDetail.ImageItem(
                            id = FanboxPostItemId("fixture-image-a-1"),
                            postId = FanboxPostId("10000002"),
                            extension = "jpg",
                            originalUrl = "https://example.invalid/a-image-1-original.jpg",
                            thumbnailUrl = "https://example.invalid/a-image-1-thumbnail.jpg",
                            aspectRatio = 1201.toFloat() / 801.toFloat(),
                        ),
                    ),
                    FanboxPostDetail.Body.Article.Block.File(
                        item = FanboxPostDetail.FileItem(
                            id = FanboxPostItemId("fixture-file-a-1"),
                            postId = FanboxPostId("10000002"),
                            name = "fixture-a-file-1.pdf",
                            extension = "pdf",
                            size = 4096,
                            url = "https://example.invalid/a-file-1.pdf",
                        ),
                    ),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture A Text 1"),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture A Text 2"),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture A Text 3"),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture A Text 4"),
                ),
            ),
            coverImageUrl = "https://example.invalid/article-a-cover.jpg",
            commentCount = 2,
            excerpt = "Fixture article A excerpt",
            feeRequired = 0,
            hasAdultContent = false,
            imageForShare = "https://example.invalid/article-a-share.jpg",
            isLiked = false,
            isBookmarked = false,
            isRestricted = false,
            likeCount = 4,
            tags = listOf("fixture-a-tag-1", "fixture-a-tag-2", "fixture-a-tag-3", "fixture-a-tag-4"),
            updatedDatetime = Instant.parse("2000-03-03T00:00:00+00:00"),
            publishedDatetime = Instant.parse("2000-03-01T00:00:00+00:00"),
            nextPost = FanboxPostDetail.OtherPost(
                id = FanboxPostId("11000001"),
                title = "Fixture A Next Post",
                publishedDatetime = Instant.parse("2000-01-02T00:00:00+00:00"),
            ),
            prevPost = FanboxPostDetail.OtherPost(
                id = FanboxPostId("11000002"),
                title = "Fixture A Prev Post",
                publishedDatetime = Instant.parse("2000-01-03T00:00:00+00:00"),
            ),
            user = FanboxUser(
                userId = FanboxUserId(91000001),
                creatorId = FanboxCreatorId("fixture-creator-article-a"),
                name = "Fixture A User",
                iconUrl = "https://example.invalid/a-user-icon.png",
            ),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun postInfoArticleBWithHeaderImageAndUrlEmbedReferencesMapsFullObject() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoArticleB),
        )

        val expected = FanboxPostDetail(
            id = FanboxPostId("10000003"),
            title = "Fixture Article B",
            body = FanboxPostDetail.Body.Article(
                blocks = listOf(
                    FanboxPostDetail.Body.Article.Block.Text("Fixture B Header 1"),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture B Text 1"),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture B Text 2"),
                    FanboxPostDetail.Body.Article.Block.Image(
                        item = FanboxPostDetail.ImageItem(
                            id = FanboxPostItemId("fixture-image-b-1"),
                            postId = FanboxPostId("10000003"),
                            extension = "jpg",
                            originalUrl = "https://example.invalid/b-image-1-original.jpg",
                            thumbnailUrl = "https://example.invalid/b-image-1-thumbnail.jpg",
                            aspectRatio = 1201.toFloat() / 801.toFloat(),
                        ),
                    ),
                    FanboxPostDetail.Body.Article.Block.Link(
                        html = null,
                        post = FanboxPost(
                            id = FanboxPostId("13000001"),
                            title = "Fixture B Embedded Post 1",
                            cover = FanboxCover(
                                url = "https://example.invalid/b-embedded-1-cover.jpg",
                                type = "cover_image",
                            ),
                            user = FanboxUser(
                                userId = FanboxUserId(92000001),
                                creatorId = FanboxCreatorId("fixture-creator-b-embedded-1"),
                                name = "Fixture B Embedded User 1",
                                iconUrl = "https://example.invalid/b-embedded-1-user.png",
                            ),
                            excerpt = "Fixture B embedded post 1 excerpt",
                            feeRequired = 0,
                            hasAdultContent = false,
                            isLiked = false,
                            isRestricted = false,
                            likeCount = 2,
                            commentCount = 1,
                            tags = listOf("fixture-embedded-tag-1"),
                            publishedDatetime = Instant.parse("2000-02-01T00:00:00+00:00"),
                            updatedDatetime = Instant.parse("2000-02-02T00:00:00+00:00"),
                        ),
                    ),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture B Text 3"),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture B Header 2"),
                    FanboxPostDetail.Body.Article.Block.Link(
                        html = "<div>Fixture B Link 4</div>",
                        post = null,
                    ),
                    FanboxPostDetail.Body.Article.Block.Link(
                        html = "<div>Fixture B Link 5</div>",
                        post = null,
                    ),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture B Text 4"),
                    FanboxPostDetail.Body.Article.Block.Link(
                        html = "<div>Fixture B Link 3</div>",
                        post = null,
                    ),
                    FanboxPostDetail.Body.Article.Block.Link(
                        html = "<div>Fixture B Link 2</div>",
                        post = null,
                    ),
                ),
            ),
            coverImageUrl = "https://example.invalid/article-b-cover.jpg",
            commentCount = 3,
            excerpt = "Fixture article B excerpt",
            feeRequired = 0,
            hasAdultContent = false,
            imageForShare = "https://example.invalid/article-b-share.jpg",
            isLiked = false,
            isBookmarked = false,
            isRestricted = false,
            likeCount = 5,
            tags = emptyList(),
            updatedDatetime = Instant.parse("2000-03-03T00:00:00+00:00"),
            publishedDatetime = Instant.parse("2000-03-02T00:00:00+00:00"),
            nextPost = FanboxPostDetail.OtherPost(
                id = FanboxPostId("12000001"),
                title = "Fixture B Next Post",
                publishedDatetime = Instant.parse("2000-01-02T00:00:00+00:00"),
            ),
            prevPost = FanboxPostDetail.OtherPost(
                id = FanboxPostId("12000002"),
                title = "Fixture B Prev Post",
                publishedDatetime = Instant.parse("2000-01-03T00:00:00+00:00"),
            ),
            user = FanboxUser(
                userId = FanboxUserId(91000002),
                creatorId = FanboxCreatorId("fixture-creator-article-b"),
                name = "Fixture B User",
                iconUrl = "https://example.invalid/b-user-icon.png",
            ),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun postInfoTextDecodesAndMapsToUnknownBody() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoText),
        )

        val expected = FanboxPostDetail(
            id = FanboxPostId("10000001"),
            title = "Fixture text post",
            body = FanboxPostDetail.Body.Unknown,
            coverImageUrl = null,
            commentCount = 0,
            excerpt = "Fixture text excerpt",
            feeRequired = 0,
            hasAdultContent = false,
            imageForShare = "https://example.invalid/text-share.jpg",
            isLiked = false,
            isBookmarked = false,
            isRestricted = false,
            likeCount = 0,
            tags = emptyList(),
            updatedDatetime = Instant.parse("2026-07-13T00:00:00+00:00"),
            publishedDatetime = Instant.parse("2026-07-13T00:00:00+00:00"),
            nextPost = null,
            prevPost = null,
            user = null,
        )

        assertEquals(expected, actual)
    }
}
