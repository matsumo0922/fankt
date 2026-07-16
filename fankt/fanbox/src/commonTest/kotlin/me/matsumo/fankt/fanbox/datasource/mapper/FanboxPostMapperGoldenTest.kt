package me.matsumo.fankt.fanbox.datasource.mapper

import kotlinx.datetime.Instant
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.PageCursorInfo
import me.matsumo.fankt.fanbox.domain.PageOffsetInfo
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPostItemsEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPostsPaginateEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostCommentListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostListEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxComment
import me.matsumo.fankt.fanbox.domain.model.FanboxCover
import me.matsumo.fankt.fanbox.domain.model.FanboxPost
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxUser
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
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

    @Test
    fun postInfoImageMapsFullObject() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoImage),
        )

        val expected = FanboxPostDetail(
            id = FanboxPostId("10000001"),
            title = "Fixture Post Title 3",
            body = FanboxPostDetail.Body.Image(
                text = "",
                images = listOf(
                    FanboxPostDetail.ImageItem(
                        id = FanboxPostItemId("fixture-image-1"),
                        postId = FanboxPostId("10000001"),
                        extension = "jpeg",
                        originalUrl = "https://example.invalid/resource-1",
                        thumbnailUrl = "https://example.invalid/resource-2",
                        aspectRatio = 3687.toFloat() / 2720.toFloat(),
                    ),
                ),
            ),
            coverImageUrl = "https://example.invalid/resource-3",
            commentCount = 0,
            excerpt = "",
            feeRequired = 0,
            hasAdultContent = true,
            imageForShare = "https://example.invalid/resource-3",
            isLiked = false,
            isBookmarked = false,
            isRestricted = false,
            likeCount = 16,
            tags = listOf("Fixture Tag 1"),
            updatedDatetime = Instant.parse("2000-01-01T00:03:00+00:00"),
            publishedDatetime = Instant.parse("2000-01-01T00:01:00+00:00"),
            nextPost = FanboxPostDetail.OtherPost(
                id = FanboxPostId("10000002"),
                title = "Fixture Post Title 1",
                publishedDatetime = Instant.parse("2000-01-01T00:02:00+00:00"),
            ),
            prevPost = FanboxPostDetail.OtherPost(
                id = FanboxPostId("10000003"),
                title = "Fixture Post Title 2",
                publishedDatetime = Instant.parse("2000-01-01T00:00:00+00:00"),
            ),
            user = FanboxUser(
                userId = FanboxUserId(90000001),
                creatorId = FanboxCreatorId("fixture-creator-1"),
                name = "Fixture User Name 1",
                iconUrl = "https://example.invalid/resource-4",
            ),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun postInfoFileMapsFullObject() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoFile),
        )

        val expected = FanboxPostDetail(
            id = FanboxPostId("10000001"),
            title = "Fixture Post Title 3",
            body = FanboxPostDetail.Body.File(
                text = "Fixture Post Text 1",
                files = listOf(
                    FanboxPostDetail.FileItem(
                        id = FanboxPostItemId("fixture-file-1"),
                        postId = FanboxPostId("10000001"),
                        name = "fixture-file-name-1.mp3",
                        extension = "mp3",
                        size = 2193140,
                        url = "https://example.invalid/resource-1",
                    ),
                    FanboxPostDetail.FileItem(
                        id = FanboxPostItemId("fixture-file-2"),
                        postId = FanboxPostId("10000001"),
                        name = "fixture-file-name-2.mp3",
                        extension = "mp3",
                        size = 1921049,
                        url = "https://example.invalid/resource-2",
                    ),
                ),
            ),
            coverImageUrl = null,
            commentCount = 0,
            excerpt = "Fixture Excerpt 1",
            feeRequired = 0,
            hasAdultContent = false,
            imageForShare = "https://example.invalid/resource-3",
            isLiked = false,
            isBookmarked = false,
            isRestricted = false,
            likeCount = 1,
            tags = listOf(
                "Fixture Tag 1",
                "Fixture Tag 2",
                "Fixture Tag 3",
                "Fixture Tag 4",
                "Fixture Tag 5",
            ),
            updatedDatetime = Instant.parse("2000-01-01T00:01:00+00:00"),
            publishedDatetime = Instant.parse("2000-01-01T00:01:00+00:00"),
            nextPost = FanboxPostDetail.OtherPost(
                id = FanboxPostId("10000002"),
                title = "Fixture Post Title 1",
                publishedDatetime = Instant.parse("2000-01-01T00:02:00+00:00"),
            ),
            prevPost = FanboxPostDetail.OtherPost(
                id = FanboxPostId("10000003"),
                title = "Fixture Post Title 2",
                publishedDatetime = Instant.parse("2000-01-01T00:00:00+00:00"),
            ),
            user = FanboxUser(
                userId = FanboxUserId(90000001),
                creatorId = FanboxCreatorId("fixture-creator-1"),
                name = "Fixture User Name 1",
                iconUrl = "https://example.invalid/resource-4",
            ),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun postListHomeNormalMapsFullPage() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostListEntity>(FanboxPostJsonFixtures.postListHomeNormal),
            endpoint = "post.listHome",
        ).value

        val expected = PageCursorInfo(
            contents = listOf(
                expectedPost(
                    id = "10000001",
                    title = "Fixture Post Title 1",
                    cover = FanboxCover(
                        url = "https://example.invalid/resource-1",
                        type = "cover_image",
                    ),
                    excerpt = "",
                    feeRequired = 500,
                    isRestricted = true,
                    likeCount = 0,
                    commentCount = 1,
                    tags = listOf("Fixture Tag 1", "Fixture Tag 2", "Fixture Tag 3"),
                    datetime = "2000-01-01T00:01:00+00:00",
                    userIconUrl = "https://example.invalid/resource-2",
                ),
                expectedPost(
                    id = "10000002",
                    title = "Fixture Post Title 2",
                    cover = null,
                    excerpt = "Fixture Excerpt 1",
                    feeRequired = 0,
                    isRestricted = false,
                    likeCount = 3,
                    commentCount = 0,
                    tags = emptyList(),
                    datetime = "2000-01-01T00:00:00+00:00",
                    userIconUrl = "https://example.invalid/resource-2",
                ),
            ),
            cursor = FanboxCursor(
                firstPublishedDatetime = null,
                maxPublishedDatetime = "2000-01-01T00:00:00+00:00",
                firstId = null,
                maxId = "10000003",
                limit = 2,
            ),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun postListHomeEmptyMapsEmptyPage() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostListEntity>(FanboxPostJsonFixtures.postListHomeEmpty),
            endpoint = "post.listHome",
        ).value

        assertEquals(PageCursorInfo<FanboxPost>(emptyList(), null), actual)
    }

    @Test
    fun postListCreatorNormalMapsFullPageWithRuntimeNextCursor() {
        val runtimeNextCursor = FanboxCursor(
            firstPublishedDatetime = "2000-02-01T00:00:00+00:00",
            maxPublishedDatetime = null,
            firstId = "19000001",
            maxId = null,
            limit = 20,
        )
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxCreatorPostItemsEntity>(FanboxPostJsonFixtures.postListCreatorNormal),
            runtimeNextCursor,
            endpoint = "post.listCreator",
        ).value

        val expected = PageCursorInfo(
            contents = listOf(
                expectedPost(
                    id = "10000001",
                    title = "Fixture Post Title 1",
                    cover = null,
                    excerpt = "Fixture Excerpt 1",
                    feeRequired = 0,
                    isRestricted = false,
                    likeCount = 0,
                    commentCount = 0,
                    tags = listOf(
                        "Fixture Tag 1",
                        "Fixture Tag 2",
                        "Fixture Tag 3",
                        "Fixture Tag 4",
                        "Fixture Tag 5",
                    ),
                    datetime = "2000-01-01T00:01:00+00:00",
                    userIconUrl = "https://example.invalid/resource-1",
                ),
                expectedPost(
                    id = "10000002",
                    title = "Fixture Post Title 2",
                    cover = null,
                    excerpt = "Fixture Excerpt 2",
                    feeRequired = 0,
                    isRestricted = false,
                    likeCount = 1,
                    commentCount = 0,
                    tags = listOf(
                        "Fixture Tag 5",
                        "Fixture Tag 4",
                        "Fixture Tag 3",
                        "Fixture Tag 2",
                        "Fixture Tag 1",
                    ),
                    datetime = "2000-01-01T00:00:00+00:00",
                    userIconUrl = "https://example.invalid/resource-1",
                ),
            ),
            cursor = runtimeNextCursor,
        )

        assertEquals(expected, actual)
    }

    @Test
    fun postListCreatorEmptyMapsEmptyPageWithExplicitNullRuntimeCursor() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxCreatorPostItemsEntity>(FanboxPostJsonFixtures.postListCreatorEmpty),
            nextCursor = null,
            endpoint = "post.listCreator",
        ).value

        assertEquals(PageCursorInfo<FanboxPost>(emptyList(), null), actual)
    }

    @Test
    fun paginateCreatorNormalMapsEveryCursor() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxCreatorPostsPaginateEntity>(FanboxPostJsonFixtures.paginateCreatorNormal),
        )

        val expected = listOf(
            expectedCursor(0, "10000001"),
            expectedCursor(1, "10000002"),
            expectedCursor(2, "10000003"),
            expectedCursor(3, "10000004"),
            expectedCursor(4, "10000005"),
            expectedCursor(5, "10000006"),
            expectedCursor(6, "10000007"),
            expectedCursor(7, "10000008"),
            expectedCursor(8, "10000009"),
            expectedCursor(9, "10000010"),
            expectedCursor(10, "10000011"),
            expectedCursor(11, "10000012"),
            expectedCursor(12, "10000013"),
            expectedCursor(13, "10000014"),
            expectedCursor(14, "10000015"),
            expectedCursor(15, "10000016"),
            expectedCursor(16, "10000017"),
            expectedCursor(17, "10000018"),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun paginateCreatorEmptyMapsEmptyCursorList() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxCreatorPostsPaginateEntity>(FanboxPostJsonFixtures.paginateCreatorEmpty),
        )

        assertEquals(emptyList(), actual)
    }

    @Test
    fun postCommentsFlatMapsBasicCommentWithoutRepliesOrNextOffset() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostCommentListEntity>(FanboxPostJsonFixtures.postCommentsFlat),
            endpoint = "post.getComments",
        ).value

        val expected = PageOffsetInfo(
            contents = listOf(
                FanboxComment(
                    body = "Fixture Comment Body 1",
                    createdDatetime = Instant.parse("2000-01-01T00:00:00+00:00"),
                    id = FanboxCommentId("20000001"),
                    isLiked = false,
                    isOwn = false,
                    likeCount = 1,
                    parentCommentId = FanboxCommentId("0"),
                    rootCommentId = FanboxCommentId("0"),
                    replies = emptyList(),
                    user = FanboxUser(
                        userId = FanboxUserId(90000001),
                        creatorId = null,
                        name = "Fixture Comment User 1",
                        iconUrl = "https://example.invalid/user-1.png",
                    ),
                ),
            ),
            offset = null,
        )

        assertEquals(expected, actual)
    }

    @Test
    fun handcraftedPostCommentsNestedRepliesMapInCreatedDatetimeOrderWithFullEquality() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostCommentListEntity>(FanboxPostJsonFixtures.handcraftedPostCommentsNestedReplies),
            endpoint = "post.getComments",
        ).value

        val expected = PageOffsetInfo(
            contents = listOf(
                FanboxComment(
                    body = "Handcrafted fixture root comment content",
                    createdDatetime = Instant.parse("2000-01-01T00:00:00+00:00"),
                    id = FanboxCommentId("handcrafted-root-comment"),
                    isLiked = false,
                    isOwn = false,
                    likeCount = 0,
                    parentCommentId = FanboxCommentId("0"),
                    rootCommentId = FanboxCommentId("0"),
                    replies = listOf(
                        FanboxComment(
                            body = "Handcrafted fixture earlier reply content",
                            createdDatetime = Instant.parse("2000-01-01T00:01:00+00:00"),
                            id = FanboxCommentId("handcrafted-earlier-reply"),
                            isLiked = false,
                            isOwn = true,
                            likeCount = 1,
                            parentCommentId = FanboxCommentId("handcrafted-root-comment"),
                            rootCommentId = FanboxCommentId("handcrafted-root-comment"),
                            replies = emptyList(),
                            user = null,
                        ),
                        FanboxComment(
                            body = "Handcrafted fixture later reply content",
                            createdDatetime = Instant.parse("2000-01-01T00:02:00+00:00"),
                            id = FanboxCommentId("handcrafted-later-reply"),
                            isLiked = true,
                            isOwn = false,
                            likeCount = 2,
                            parentCommentId = FanboxCommentId("handcrafted-root-comment"),
                            rootCommentId = FanboxCommentId("handcrafted-root-comment"),
                            replies = emptyList(),
                            user = null,
                        ),
                    ),
                    user = null,
                ),
            ),
            offset = null,
        )

        assertEquals(expected, actual)
    }

    @Test
    fun handcraftedPostCommentsNextUrlMapsOffsetWithFullEquality() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostCommentListEntity>(FanboxPostJsonFixtures.handcraftedPostCommentsNextUrlOffset),
            endpoint = "post.getComments",
        ).value

        val expected = PageOffsetInfo<FanboxComment>(
            contents = emptyList(),
            offset = 23,
        )

        assertEquals(expected, actual)
    }

    private fun expectedPost(
        id: String,
        title: String,
        cover: FanboxCover?,
        excerpt: String,
        feeRequired: Int,
        isRestricted: Boolean,
        likeCount: Int,
        commentCount: Int,
        tags: List<String>,
        datetime: String,
        userIconUrl: String,
    ) = FanboxPost(
        id = FanboxPostId(id),
        title = title,
        cover = cover,
        user = FanboxUser(
            userId = FanboxUserId(90000001),
            creatorId = FanboxCreatorId("fixture-creator-1"),
            name = "Fixture User Name 1",
            iconUrl = userIconUrl,
        ),
        excerpt = excerpt,
        feeRequired = feeRequired,
        hasAdultContent = false,
        isLiked = false,
        isRestricted = isRestricted,
        likeCount = likeCount,
        commentCount = commentCount,
        tags = tags,
        publishedDatetime = Instant.parse(datetime),
        updatedDatetime = Instant.parse(datetime),
    )

    private fun expectedCursor(minute: Int, firstId: String) = FanboxCursor(
        firstPublishedDatetime = "2000-01-01T00:${minute.toString().padStart(2, '0')}:00+00:00",
        maxPublishedDatetime = null,
        firstId = firstId,
        maxId = null,
        limit = 10,
    )
}
