package me.matsumo.fankt.fanbox.datasource.mapper

import kotlinx.datetime.Instant
import me.matsumo.fankt.fanbox.domain.PageNumberInfo
import me.matsumo.fankt.fanbox.domain.entity.FanboxBellListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxNewsLettersEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPaidRecordListEntity
import me.matsumo.fankt.fanbox.domain.model.FanboxBell
import me.matsumo.fankt.fanbox.domain.model.FanboxCover
import me.matsumo.fankt.fanbox.domain.model.FanboxCreator
import me.matsumo.fankt.fanbox.domain.model.FanboxNewsLetter
import me.matsumo.fankt.fanbox.domain.model.FanboxPaidRecord
import me.matsumo.fankt.fanbox.domain.model.FanboxPaymentMethod
import me.matsumo.fankt.fanbox.domain.model.FanboxPost
import me.matsumo.fankt.fanbox.domain.model.FanboxUser
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxNewsLetterId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.fixture.FanboxPaymentJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxUserJsonFixtures
import me.matsumo.fankt.fanbox.fixture.decodeFixture
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxUserMapperGoldenTest {

    private val mapper = FanboxUserMapper(
        postMapper = FanboxPostMapper(),
        creatorMapper = FanboxCreatorMapper(),
    )

    @Test
    fun paidRecordsWithBodyPaymentsWrapperMapFullObjects() {
        val actual = mapper.map(
            decodeFixture<FanboxPaidRecordListEntity>(FanboxPaymentJsonFixtures.paidRecords),
        )

        val expected = listOf(
            FanboxPaidRecord(
                id = "fixture-paid-record-1",
                paidAmount = 987654321,
                paymentDateTime = Instant.parse("2000-01-01T00:00:00+00:00"),
                paymentMethod = FanboxPaymentMethod.CARD,
                creator = FanboxCreator(
                    creatorId = FanboxCreatorId("fixture-paid-creator-1"),
                    user = FanboxUser(
                        userId = FanboxUserId(94000001),
                        creatorId = FanboxCreatorId("fixture-paid-creator-1"),
                        name = "Fixture Paid User 1",
                        iconUrl = "https://example.invalid/paid-user-1.png",
                    ),
                ),
            ),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun actualBellPostPublishedPageWithPagingMapsFullObjects() {
        val actual = mapper.map(
            decodeFixture<FanboxBellListEntity>(FanboxUserJsonFixtures.actualBellPostPublishedPage),
            endpoint = "bell.list",
        ).value

        val expected: PageNumberInfo<FanboxBell> = PageNumberInfo(
            contents = listOf(
                expectedPostPublishedBell(
                    postId = "fixture-bell-2",
                    notifiedDatetime = "2000-01-01T00:01:00+00:00",
                    title = "Fixture Title 1",
                    feeRequired = 90000001,
                    publishedDatetime = "2000-01-01T00:02:00+00:00",
                    updatedDatetime = "2000-01-01T00:03:00+00:00",
                    tags = listOf("Fixture Tag 1", "Fixture Tag 2", "Fixture Tag 3"),
                    likeCount = 1,
                    commentCount = 2,
                    isRestricted = true,
                    userId = 91000001,
                    userName = "Fixture Name 1",
                    userIconUrl = "https://example.invalid/resource-1",
                    creatorId = "fixture-creator-1",
                    coverUrl = "https://example.invalid/resource-2",
                    excerpt = "Fixture text 1",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-4",
                    notifiedDatetime = "2000-01-01T00:04:00+00:00",
                    title = "Fixture Title 2",
                    feeRequired = 90000002,
                    publishedDatetime = "2000-01-01T00:05:00+00:00",
                    updatedDatetime = "2000-01-01T00:06:00+00:00",
                    tags = emptyList(),
                    likeCount = 3,
                    commentCount = 4,
                    isRestricted = false,
                    userId = 91000002,
                    userName = "Fixture Name 2",
                    userIconUrl = "https://example.invalid/resource-3",
                    creatorId = "fixture-creator-2",
                    coverUrl = null,
                    excerpt = "Fixture text 2",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-6",
                    notifiedDatetime = "2000-01-01T00:07:00+00:00",
                    title = "Fixture Title 3",
                    feeRequired = 90000003,
                    publishedDatetime = "2000-01-01T00:08:00+00:00",
                    updatedDatetime = "2000-01-01T00:09:00+00:00",
                    tags = listOf("Fixture Tag 4", "Fixture Tag 5", "Fixture Tag 6"),
                    likeCount = 5,
                    commentCount = 6,
                    isRestricted = true,
                    userId = 91000003,
                    userName = "Fixture Name 3",
                    userIconUrl = "https://example.invalid/resource-4",
                    creatorId = "fixture-creator-3",
                    coverUrl = "https://example.invalid/resource-5",
                    excerpt = "Fixture text 3",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-8",
                    notifiedDatetime = "2000-01-01T00:10:00+00:00",
                    title = "Fixture Title 4",
                    feeRequired = 90000004,
                    publishedDatetime = "2000-01-01T00:11:00+00:00",
                    updatedDatetime = "2000-01-01T00:12:00+00:00",
                    tags = listOf("Fixture Tag 7", "Fixture Tag 8", "Fixture Tag 9", "Fixture Tag 10", "Fixture Tag 11", "Fixture Tag 12"),
                    likeCount = 7,
                    commentCount = 8,
                    isRestricted = false,
                    userId = 91000004,
                    userName = "Fixture Name 4",
                    userIconUrl = "https://example.invalid/resource-6",
                    creatorId = "fixture-creator-4",
                    coverUrl = "https://example.invalid/resource-7",
                    excerpt = "Fixture text 4",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-10",
                    notifiedDatetime = "2000-01-01T00:13:00+00:00",
                    title = "Fixture Title 5",
                    feeRequired = 90000005,
                    publishedDatetime = "2000-01-01T00:14:00+00:00",
                    updatedDatetime = "2000-01-01T00:15:00+00:00",
                    tags = listOf("Fixture Tag 13", "Fixture Tag 14", "Fixture Tag 15", "Fixture Tag 16"),
                    likeCount = 9,
                    commentCount = 10,
                    isRestricted = true,
                    userId = 91000005,
                    userName = "Fixture Name 5",
                    userIconUrl = "https://example.invalid/resource-8",
                    creatorId = "fixture-creator-5",
                    coverUrl = "https://example.invalid/resource-9",
                    excerpt = "Fixture text 5",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-12",
                    notifiedDatetime = "2000-01-01T00:16:00+00:00",
                    title = "Fixture Title 6",
                    feeRequired = 90000006,
                    publishedDatetime = "2000-01-01T00:17:00+00:00",
                    updatedDatetime = "2000-01-01T00:18:00+00:00",
                    tags = listOf("Fixture Tag 17", "Fixture Tag 18", "Fixture Tag 19", "Fixture Tag 20"),
                    likeCount = 11,
                    commentCount = 12,
                    isRestricted = false,
                    userId = 91000006,
                    userName = "Fixture Name 6",
                    userIconUrl = "https://example.invalid/resource-10",
                    creatorId = "fixture-creator-6",
                    coverUrl = "https://example.invalid/resource-11",
                    excerpt = "Fixture text 6",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-14",
                    notifiedDatetime = "2000-01-01T00:19:00+00:00",
                    title = "Fixture Title 7",
                    feeRequired = 90000007,
                    publishedDatetime = "2000-01-01T00:20:00+00:00",
                    updatedDatetime = "2000-01-01T00:21:00+00:00",
                    tags = emptyList(),
                    likeCount = 13,
                    commentCount = 14,
                    isRestricted = true,
                    userId = 91000007,
                    userName = "Fixture Name 7",
                    userIconUrl = "https://example.invalid/resource-12",
                    creatorId = "fixture-creator-7",
                    coverUrl = "https://example.invalid/resource-13",
                    excerpt = "Fixture text 7",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-16",
                    notifiedDatetime = "2000-01-01T00:22:00+00:00",
                    title = "Fixture Title 8",
                    feeRequired = 90000008,
                    publishedDatetime = "2000-01-01T00:23:00+00:00",
                    updatedDatetime = "2000-01-01T00:24:00+00:00",
                    tags = listOf("Fixture Tag 21", "Fixture Tag 22"),
                    likeCount = 15,
                    commentCount = 16,
                    isRestricted = false,
                    userId = 91000008,
                    userName = "Fixture Name 8",
                    userIconUrl = "https://example.invalid/resource-14",
                    creatorId = "fixture-creator-8",
                    coverUrl = "https://example.invalid/resource-15",
                    excerpt = "Fixture text 8",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-18",
                    notifiedDatetime = "2000-01-01T00:25:00+00:00",
                    title = "Fixture Title 9",
                    feeRequired = 90000009,
                    publishedDatetime = "2000-01-01T00:26:00+00:00",
                    updatedDatetime = "2000-01-01T00:27:00+00:00",
                    tags = listOf("Fixture Tag 23", "Fixture Tag 24", "Fixture Tag 25", "Fixture Tag 26"),
                    likeCount = 17,
                    commentCount = 18,
                    isRestricted = true,
                    userId = 91000009,
                    userName = "Fixture Name 9",
                    userIconUrl = "https://example.invalid/resource-16",
                    creatorId = "fixture-creator-9",
                    coverUrl = "https://example.invalid/resource-17",
                    excerpt = "Fixture text 9",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-20",
                    notifiedDatetime = "2000-01-01T00:28:00+00:00",
                    title = "Fixture Title 10",
                    feeRequired = 90000010,
                    publishedDatetime = "2000-01-01T00:29:00+00:00",
                    updatedDatetime = "2000-01-01T00:30:00+00:00",
                    tags = listOf("Fixture Tag 27", "Fixture Tag 28", "Fixture Tag 29", "Fixture Tag 30", "Fixture Tag 31", "Fixture Tag 32"),
                    likeCount = 19,
                    commentCount = 20,
                    isRestricted = false,
                    userId = 91000010,
                    userName = "Fixture Name 10",
                    userIconUrl = "https://example.invalid/resource-18",
                    creatorId = "fixture-creator-10",
                    coverUrl = "https://example.invalid/resource-19",
                    excerpt = "Fixture text 10",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-22",
                    notifiedDatetime = "2000-01-01T00:31:00+00:00",
                    title = "Fixture Title 11",
                    feeRequired = 90000011,
                    publishedDatetime = "2000-01-01T00:32:00+00:00",
                    updatedDatetime = "2000-01-01T00:33:00+00:00",
                    tags = listOf("Fixture Tag 33", "Fixture Tag 34", "Fixture Tag 35", "Fixture Tag 36"),
                    likeCount = 21,
                    commentCount = 22,
                    isRestricted = true,
                    userId = 91000011,
                    userName = "Fixture Name 11",
                    userIconUrl = "https://example.invalid/resource-20",
                    creatorId = "fixture-creator-11",
                    coverUrl = "https://example.invalid/resource-21",
                    excerpt = "Fixture text 11",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-24",
                    notifiedDatetime = "2000-01-01T00:34:00+00:00",
                    title = "Fixture Title 12",
                    feeRequired = 90000012,
                    publishedDatetime = "2000-01-01T00:35:00+00:00",
                    updatedDatetime = "2000-01-01T00:36:00+00:00",
                    tags = listOf("Fixture Tag 37", "Fixture Tag 38", "Fixture Tag 39", "Fixture Tag 40"),
                    likeCount = 23,
                    commentCount = 24,
                    isRestricted = false,
                    userId = 91000012,
                    userName = "Fixture Name 12",
                    userIconUrl = "https://example.invalid/resource-22",
                    creatorId = "fixture-creator-12",
                    coverUrl = "https://example.invalid/resource-23",
                    excerpt = "Fixture text 12",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-26",
                    notifiedDatetime = "2000-01-01T00:37:00+00:00",
                    title = "Fixture Title 13",
                    feeRequired = 90000013,
                    publishedDatetime = "2000-01-01T00:38:00+00:00",
                    updatedDatetime = "2000-01-01T00:39:00+00:00",
                    tags = emptyList(),
                    likeCount = 25,
                    commentCount = 26,
                    isRestricted = false,
                    userId = 91000013,
                    userName = "Fixture Name 13",
                    userIconUrl = "https://example.invalid/resource-24",
                    creatorId = "fixture-creator-13",
                    coverUrl = null,
                    excerpt = "Fixture text 13",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-28",
                    notifiedDatetime = "2000-01-01T00:40:00+00:00",
                    title = "Fixture Title 14",
                    feeRequired = 90000014,
                    publishedDatetime = "2000-01-01T00:41:00+00:00",
                    updatedDatetime = "2000-01-01T00:42:00+00:00",
                    tags = emptyList(),
                    likeCount = 27,
                    commentCount = 28,
                    isRestricted = false,
                    userId = 91000014,
                    userName = "Fixture Name 14",
                    userIconUrl = "https://example.invalid/resource-25",
                    creatorId = "fixture-creator-14",
                    coverUrl = "https://example.invalid/resource-26",
                    excerpt = "Fixture text 14",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-30",
                    notifiedDatetime = "2000-01-01T00:43:00+00:00",
                    title = "Fixture Title 15",
                    feeRequired = 90000015,
                    publishedDatetime = "2000-01-01T00:44:00+00:00",
                    updatedDatetime = "2000-01-01T00:45:00+00:00",
                    tags = emptyList(),
                    likeCount = 29,
                    commentCount = 30,
                    isRestricted = true,
                    userId = 91000015,
                    userName = "Fixture Name 15",
                    userIconUrl = "https://example.invalid/resource-27",
                    creatorId = "fixture-creator-15",
                    coverUrl = "https://example.invalid/resource-28",
                    excerpt = "Fixture text 15",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-32",
                    notifiedDatetime = "2000-01-01T00:46:00+00:00",
                    title = "Fixture Title 16",
                    feeRequired = 90000016,
                    publishedDatetime = "2000-01-01T00:47:00+00:00",
                    updatedDatetime = "2000-01-01T00:48:00+00:00",
                    tags = listOf("Fixture Tag 41", "Fixture Tag 42", "Fixture Tag 43", "Fixture Tag 44", "Fixture Tag 45"),
                    likeCount = 31,
                    commentCount = 32,
                    isRestricted = true,
                    userId = 91000016,
                    userName = "Fixture Name 16",
                    userIconUrl = "https://example.invalid/resource-29",
                    creatorId = "fixture-creator-16",
                    coverUrl = "https://example.invalid/resource-30",
                    excerpt = "Fixture text 16",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-34",
                    notifiedDatetime = "2000-01-01T00:49:00+00:00",
                    title = "Fixture Title 17",
                    feeRequired = 90000017,
                    publishedDatetime = "2000-01-01T00:50:00+00:00",
                    updatedDatetime = "2000-01-01T00:51:00+00:00",
                    tags = listOf("Fixture Tag 46", "Fixture Tag 47", "Fixture Tag 48", "Fixture Tag 49", "Fixture Tag 50", "Fixture Tag 51"),
                    likeCount = 33,
                    commentCount = 34,
                    isRestricted = false,
                    userId = 91000017,
                    userName = "Fixture Name 17",
                    userIconUrl = "https://example.invalid/resource-31",
                    creatorId = "fixture-creator-17",
                    coverUrl = "https://example.invalid/resource-32",
                    excerpt = "Fixture text 17",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-36",
                    notifiedDatetime = "2000-01-01T00:52:00+00:00",
                    title = "Fixture Title 18",
                    feeRequired = 90000018,
                    publishedDatetime = "2000-01-01T00:53:00+00:00",
                    updatedDatetime = "2000-01-01T00:54:00+00:00",
                    tags = listOf("Fixture Tag 52", "Fixture Tag 53", "Fixture Tag 54", "Fixture Tag 55", "Fixture Tag 56"),
                    likeCount = 35,
                    commentCount = 36,
                    isRestricted = true,
                    userId = 91000018,
                    userName = "Fixture Name 18",
                    userIconUrl = "https://example.invalid/resource-33",
                    creatorId = "fixture-creator-18",
                    coverUrl = "https://example.invalid/resource-34",
                    excerpt = "Fixture text 18",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-38",
                    notifiedDatetime = "2000-01-01T00:55:00+00:00",
                    title = "Fixture Title 19",
                    feeRequired = 90000019,
                    publishedDatetime = "2000-01-01T00:56:00+00:00",
                    updatedDatetime = "2000-01-01T00:57:00+00:00",
                    tags = listOf("Fixture Tag 57", "Fixture Tag 58", "Fixture Tag 59", "Fixture Tag 60"),
                    likeCount = 37,
                    commentCount = 38,
                    isRestricted = false,
                    userId = 91000019,
                    userName = "Fixture Name 19",
                    userIconUrl = "https://example.invalid/resource-35",
                    creatorId = "fixture-creator-19",
                    coverUrl = "https://example.invalid/resource-36",
                    excerpt = "Fixture text 19",
                ),
                expectedPostPublishedBell(
                    postId = "fixture-bell-40",
                    notifiedDatetime = "2000-01-01T00:58:00+00:00",
                    title = "Fixture Title 20",
                    feeRequired = 90000020,
                    publishedDatetime = "2000-01-01T00:59:00+00:00",
                    updatedDatetime = "2000-01-01T00:00:00+00:00",
                    tags = listOf("Fixture Tag 61"),
                    likeCount = 39,
                    commentCount = 40,
                    isRestricted = true,
                    userId = 91000020,
                    userName = "Fixture Name 20",
                    userIconUrl = "https://example.invalid/resource-37",
                    creatorId = "fixture-creator-20",
                    coverUrl = "https://example.invalid/resource-38",
                    excerpt = "Fixture text 20",
                ),
            ),
            nextPage = 2,
        )

        assertEquals(expected, actual)
    }

    @Test
    fun handcraftedNewsletterVariationMapsFullObject() {
        val actual = mapper.map(
            decodeFixture<FanboxNewsLettersEntity>(FanboxUserJsonFixtures.handcraftedNewsletterList),
        )

        val expected = listOf(
            FanboxNewsLetter(
                id = FanboxNewsLetterId("handcrafted-newsletter-1"),
                body = "Handcrafted newsletter body",
                createdAt = Instant.parse("2000-02-01T00:00:00+00:00"),
                creator = FanboxCreator(
                    creatorId = FanboxCreatorId("handcrafted-newsletter-creator"),
                    user = FanboxUser(
                        userId = FanboxUserId(95000001),
                        creatorId = FanboxCreatorId("handcrafted-newsletter-creator"),
                        name = "Handcrafted Newsletter Creator",
                        iconUrl = "https://example.invalid/handcrafted-newsletter-user.png",
                    ),
                ),
                isRead = false,
            ),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun handcraftedBellPostCommentVariationMapsFullObject() {
        val actual = mapper.map(
            decodeFixture<FanboxBellListEntity>(FanboxUserJsonFixtures.handcraftedBellPostComment),
            endpoint = "bell.list",
        ).value

        val expected: PageNumberInfo<FanboxBell> = PageNumberInfo(
            contents = listOf(
                FanboxBell.Comment(
                    id = FanboxCommentId("handcrafted-comment-notification-1"),
                    notifiedDatetime = Instant.parse("2000-02-02T00:00:00+00:00"),
                    comment = "Handcrafted comment body",
                    isRootComment = true,
                    creatorId = FanboxCreatorId("handcrafted-comment-creator"),
                    postId = FanboxPostId("95000002"),
                    postTitle = "Handcrafted comment post",
                    userName = "Handcrafted Commenter",
                    userProfileIconUrl = "https://example.invalid/handcrafted-commenter.png",
                ),
            ),
            nextPage = null,
        )

        assertEquals(expected, actual)
    }

    @Test
    fun handcraftedBellPostCommentLikeVariationMapsFullObject() {
        val actual = mapper.map(
            decodeFixture<FanboxBellListEntity>(FanboxUserJsonFixtures.handcraftedBellPostCommentLike),
            endpoint = "bell.list",
        ).value

        val expected: PageNumberInfo<FanboxBell> = PageNumberInfo(
            contents = listOf(
                FanboxBell.Like(
                    id = "handcrafted-like-notification-1",
                    notifiedDatetime = Instant.parse("2000-02-03T00:00:00+00:00"),
                    comment = "Handcrafted liked comment body",
                    creatorId = FanboxCreatorId("handcrafted-like-creator"),
                    postId = FanboxPostId("95000003"),
                    count = 3,
                ),
            ),
            nextPage = null,
        )

        assertEquals(expected, actual)
    }

    private fun expectedPostPublishedBell(
        postId: String,
        notifiedDatetime: String,
        title: String,
        feeRequired: Int,
        publishedDatetime: String,
        updatedDatetime: String,
        tags: List<String>,
        likeCount: Int,
        commentCount: Int,
        isRestricted: Boolean,
        userId: Long,
        userName: String,
        userIconUrl: String,
        creatorId: String,
        coverUrl: String?,
        excerpt: String,
    ): FanboxBell.PostPublished {
        return FanboxBell.PostPublished(
            id = FanboxPostId(postId),
            notifiedDatetime = Instant.parse(notifiedDatetime),
            post = FanboxPost(
                id = FanboxPostId(postId),
                title = title,
                cover = coverUrl?.let { FanboxCover(url = it, type = "cover_image") },
                user = FanboxUser(
                    userId = FanboxUserId(userId),
                    creatorId = FanboxCreatorId(creatorId),
                    name = userName,
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
                publishedDatetime = Instant.parse(publishedDatetime),
                updatedDatetime = Instant.parse(updatedDatetime),
            ),
        )
    }
}
