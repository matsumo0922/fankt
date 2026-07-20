@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox.datasource.mapper

import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.PageCursorInfo
import me.matsumo.fankt.fanbox.domain.PageOffsetInfo
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPostItemsEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorPostsPaginateEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostCommentListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostEntity
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
import kotlin.test.assertIs
import kotlin.time.Instant

class FanboxPostMapperGoldenTest {

    @Test
    fun postTimestampsParseExplicitOffsetsAsStdlibInstants() {
        val actual = FanboxPostMapper().map(
            FanboxPostEntity(
                commentCount = 0,
                cover = null,
                creatorId = "fixture-creator",
                excerpt = "Fixture excerpt",
                feeRequired = 0,
                hasAdultContent = false,
                id = "fixture-post",
                isLiked = false,
                isRestricted = false,
                likeCount = 0,
                publishedDatetime = "2025-01-02T03:04:05+09:00",
                tags = emptyList(),
                title = "Fixture post",
                updatedDatetime = "2025-01-02T03:05:06-04:00",
                user = null,
            ),
        )

        assertEquals(Instant.parse("2025-01-02T03:04:05+09:00"), actual.publishedDatetime)
        assertEquals(Instant.parse("2025-01-02T03:05:06-04:00"), actual.updatedDatetime)
    }

    @Test
    fun actualDerivedTextFragmentsPreserveMetadataAndEmptyParagraphOrder() {
        val body = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoArticleTextMetadata),
        ).body
        val blocks = assertIs<FanboxPostDetail.Body.Article>(body).blocks

        assertEquals(true, assertIs<FanboxPostDetail.Body.Article.Block.Text>(blocks[0]).isHeader)
        val styled = assertIs<FanboxPostDetail.Body.Article.Block.Text>(blocks[1])
        assertEquals(listOf("bold"), styled.styles.map { it.type })
        assertEquals(listOf(0), styled.styles.map { it.offset })
        assertEquals(listOf(7), styled.styles.map { it.length })
        assertEquals("https://example.invalid/inline", styled.links.single().url)
        assertEquals(FanboxPostDetail.Body.Article.Block.Text(""), blocks[2])
    }

    @Test
    fun syntheticTextSpansPreserveUnknownTypeAndOmitIncompleteValues() {
        val body = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoArticleSyntheticTextSpans),
        ).body
        val blocks = assertIs<FanboxPostDetail.Body.Article>(body).blocks

        assertEquals(
            listOf(FanboxPostDetail.Body.Article.Block.Text.StyleSpan("future-style", 8, 4)),
            assertIs<FanboxPostDetail.Body.Article.Block.Text>(blocks[0]).styles,
        )
        assertEquals(
            FanboxPostDetail.Body.Article.Block.Text("Fixture incomplete spans"),
            blocks[1],
        )
    }

    @Test
    fun actualDerivedUrlEmbedFragmentsPreserveTypeSpecificMetadata() {
        val body = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoArticleUrlEmbeds),
        ).body
        val links = assertIs<FanboxPostDetail.Body.Article>(body).blocks
            .filterIsInstance<FanboxPostDetail.Body.Article.Block.Link>()

        assertEquals(4, links.size)
        assertEquals(
            FanboxPostDetail.Body.Article.Block.Link(
                html = null,
                post = null,
                type = "default",
                url = "https://example.invalid/plain-link",
            ),
            links[0],
        )
        assertEquals(
            FanboxPostDetail.Body.Article.Block.Link(
                html = "<div>Fixture HTML embed</div>",
                post = null,
                type = "html",
            ),
            links[1],
        )
        assertEquals(
            FanboxPostDetail.Body.Article.Block.Link(
                html = "<article>Fixture HTML card embed</article>",
                post = null,
                type = "html.card",
            ),
            links[2],
        )
        assertEquals("fanbox.post", links[3].type)
        assertEquals(null, links[3].url)
        assertEquals(null, links[3].html)
        assertEquals(FanboxPostId("13000001"), links[3].post?.id)
    }

    @Test
    fun unknownUrlEmbedTypePreservesAllReceivedMetadata() {
        val body = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoArticleUnknownUrlEmbed),
        ).body
        val link = assertIs<FanboxPostDetail.Body.Article>(body).blocks
            .filterIsInstance<FanboxPostDetail.Body.Article.Block.Link>()
            .single()

        assertEquals("future.embed", link.type)
        assertEquals("https://example.invalid/future-link", link.url)
        assertEquals("<aside>Fixture future embed</aside>", link.html)
        assertEquals(FanboxPostId("13000001"), link.post?.id)
    }

    @Test
    fun syntheticArticleEmbedsMapProvidersAndFallbacksInOrder() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoArticleEmbeds),
        ).body

        val expected = FanboxPostDetail.Body.Article(
            blocks = listOf(
                FanboxPostDetail.Body.Article.Block.Text("Fixture Before"),
                FanboxPostDetail.Body.Article.Block.Embed("twitter", "twitter-content"),
                FanboxPostDetail.Body.Article.Block.Embed("youtube", "youtube-video"),
                FanboxPostDetail.Body.Article.Block.Embed("vimeo", "vimeo-content"),
                FanboxPostDetail.Body.Article.Block.Embed("soundcloud", "soundcloud-content"),
                FanboxPostDetail.Body.Article.Block.Embed("google_forms", "google-forms-content"),
                FanboxPostDetail.Body.Article.Block.Embed("fanbox", "fanbox-content"),
                FanboxPostDetail.Body.Article.Block.Unknown(
                    """{"id":"fixture-embed-unknown","serviceProvider":"future-provider","contentId":"future-content"}""",
                ),
                FanboxPostDetail.Body.Article.Block.Unknown(
                    """{"type":"future-block","text":{"malformed":true},"futureField":"fixture-future-block-value"}""",
                ),
                FanboxPostDetail.Body.Article.Block.Unknown(
                    """{"id":"fixture-embed-missing-provider","contentId":"missing-provider-content"}""",
                ),
                FanboxPostDetail.Body.Article.Block.Unknown(
                    """{"type":"embed","embedId":"fixture-embed-missing"}""",
                ),
                FanboxPostDetail.Body.Article.Block.Unknown(
                    """{"type":"image","imageId":"fixture-image-missing"}""",
                ),
                FanboxPostDetail.Body.Article.Block.Unknown(
                    """{"type":"file","fileId":"fixture-file-missing"}""",
                ),
                FanboxPostDetail.Body.Article.Block.Unknown(
                    """{"type":"url_embed","urlEmbedId":"fixture-url-missing"}""",
                ),
                FanboxPostDetail.Body.Article.Block.Text("Fixture After"),
            ),
        )

        assertEquals(expected, actual)
    }

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
                    FanboxPostDetail.Body.Article.Block.Text(""),
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
                    FanboxPostDetail.Body.Article.Block.Text(""),
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
                    FanboxPostDetail.Body.Article.Block.Text(""),
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
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture B Header 1", isHeader = true),
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
                    FanboxPostDetail.Body.Article.Block.Text(""),
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
                        type = "fanbox.post",
                    ),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture B Text 3"),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture B Header 2", isHeader = true),
                    FanboxPostDetail.Body.Article.Block.Link(
                        html = "<div>Fixture B Link 4</div>",
                        post = null,
                        type = "html",
                    ),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Link(
                        html = "<div>Fixture B Link 5</div>",
                        post = null,
                        type = "html",
                    ),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Text("Fixture B Text 4"),
                    FanboxPostDetail.Body.Article.Block.Link(
                        html = "<div>Fixture B Link 3</div>",
                        post = null,
                        type = "html",
                    ),
                    FanboxPostDetail.Body.Article.Block.Text(""),
                    FanboxPostDetail.Body.Article.Block.Link(
                        html = "<div>Fixture B Link 2</div>",
                        post = null,
                        type = "html",
                    ),
                    FanboxPostDetail.Body.Article.Block.Text(""),
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
    fun nullLegacyTextBodyMapsToUnknown() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoText),
        )

        val expected = FanboxPostDetail(
            id = FanboxPostId("10000001"),
            title = "Fixture text post",
            body = FanboxPostDetail.Body.Unknown(
                type = "text",
                rawBodyJson = null,
            ),
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
    fun hybridTextMapsToText() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoTextHybrid),
        )

        assertEquals(FanboxPostDetail.Body.Text("Fixture legacy text"), actual.body)
    }

    @Test
    fun hybridVideoUsesVideoIdBeforeContentId() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoVideo),
        )

        assertEquals(
            FanboxPostDetail.Body.Video("youtube", "fixture-video-1"),
            actual.body,
        )
    }

    @Test
    fun hybridVideoAcceptsContentIdSpelling() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoVideoContentId),
        )

        assertEquals(FanboxPostDetail.Body.Video("vimeo", "fixture-video-2"), actual.body)
    }

    @Test
    fun hybridEntryMapsToHtml() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoEntry),
        )

        assertEquals(FanboxPostDetail.Body.Html("<p>Fixture legacy HTML</p>"), actual.body)
    }

    @Test
    fun malformedLegacyBodiesPreserveRawUnknownFallback() {
        val cases = listOf(
            FanboxPostJsonFixtures.postInfoMalformedText to
                FanboxPostDetail.Body.Unknown("text", """{"text":{"unexpected":true}}"""),
            FanboxPostJsonFixtures.postInfoMalformedVideo to
                FanboxPostDetail.Body.Unknown("video", """{"video":"unexpected"}"""),
            FanboxPostJsonFixtures.postInfoMalformedEntry to
                FanboxPostDetail.Body.Unknown("entry", """{"html":{"unexpected":true}}"""),
            FanboxPostJsonFixtures.postInfoNullVideo to FanboxPostDetail.Body.Unknown("video", null),
        )

        cases.forEach { (fixture, expected) ->
            val actual = FanboxPostMapper().map(decodeFixture<FanboxPostDetailEntity>(fixture))
            assertEquals(expected, actual.body)
        }
    }

    @Test
    fun syntheticUnknownTypeReturnsPostDetailWithRawBody() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoUnknownType),
        )

        assertEquals(FanboxPostId("10000001"), actual.id)
        assertEquals(
            FanboxPostDetail.Body.Unknown(
                type = "future-post-type",
                rawBodyJson = """{"futureField":"fixture-future-value"}""",
            ),
            actual.body,
        )
    }

    @Test
    fun explicitFileTypeWinsOverNonEmptyImagePayloadFields() {
        val actual = FanboxPostMapper().map(
            decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoFileTypeWithImagePayloadFields),
        )

        assertEquals(
            FanboxPostDetail.Body.File(text = "", files = emptyList()),
            actual.body,
        )
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
