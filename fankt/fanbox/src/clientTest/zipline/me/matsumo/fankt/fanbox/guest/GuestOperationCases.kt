package me.matsumo.fankt.fanbox.guest

import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor

/**
 * guest の全 operation を、合成した最小の response で呼び出す。
 *
 * native の guest と bridge 越しの guest で同じ呼び出しを行い、結果を比べるテストが使う。
 * 戻り値の型ごとに bridge を越えられることを確かめるのが目的なので、各 operation を一度ずつ呼ぶ。
 */
internal object GuestOperationCases {
    val cursor = FanboxCursor(
        firstPublishedDatetime = "2026-01-01 00:00:00",
        maxPublishedDatetime = "2026-02-01 00:00:00",
        firstId = "100",
        maxId = "200",
        limit = 10,
    )

    private const val USER = """{"iconUrl":"https://example.invalid/icon.png","name":"Fixture User","userId":"91000001"}"""

    private const val POST =
        """{"commentCount":1,"cover":{"type":"cover_image","url":"https://example.invalid/cover.png"},"creatorId":"fixture","excerpt":"Fixture excerpt","feeRequired":500,"hasAdultContent":false,"id":"10000001","isLiked":true,"isRestricted":false,"likeCount":2,"publishedDatetime":"2026-01-01T00:00:00+09:00","tags":["fixture"],"title":"Fixture post","updatedDatetime":"2026-01-02T00:00:00+09:00","user":$USER}"""

    // The second item lacks its required fields, so the skipped item's report also crosses the bridge.
    private const val POST_LIST =
        """{"body":{"items":[$POST,{"id":"10000002"}],"nextUrl":"https://api.fanbox.cc/post.listHome?maxPublishedDatetime=2026-01-01%2000%3A00%3A00&maxId=10000001&limit=10"}}"""

    private const val CREATOR_POSTS = """{"body":{"posts":[$POST]}}"""

    private const val PAGINATION =
        """{"body":{"pageUrls":["https://api.fanbox.cc/post.listCreator?creatorId=fixture&firstPublishedDatetime=2026-01-01%2000%3A00%3A00&firstId=10000001&limit=10"]}}"""

    private const val POST_DETAIL =
        """{"body":{"post":{"body":{"text":"Fixture text"},"commentCount":0,"creatorId":"fixture","excerpt":"Fixture excerpt","feeRequired":0,"hasAdultContent":false,"id":"10000001","imageForShare":"https://example.invalid/share.png","isLiked":false,"isRestricted":false,"likeCount":0,"nextPost":null,"prevPost":null,"publishedDatetime":"2026-01-01T00:00:00Z","tags":[],"title":"Fixture title","type":"text","updatedDatetime":"2026-01-01T00:00:00Z","coverImageUrl":null,"user":$USER}}}"""

    private const val COMMENT =
        """{"body":"Fixture comment","createdDatetime":"2026-01-01T00:00:00+09:00","id":"92000001","isLiked":false,"isOwn":true,"likeCount":1,"parentCommentId":"0","rootCommentId":"0","user":$USER,"replies":[]}"""

    private const val COMMENTS =
        """{"body":{"viewMode":"fixture","commentList":{"items":[$COMMENT],"nextUrl":"https://api.fanbox.cc/post.getComments?postId=10000001&offset=10&limit=10"}}}"""

    private const val TAGGED_POSTS =
        """{"body":{"count":1,"items":[$POST],"nextUrl":"https://api.fanbox.cc/post.listTagged?tag=fixture&page=2"}}"""

    private const val CREATOR =
        """{"coverImageUrl":"https://example.invalid/cover.png","creatorId":"fixture","description":"Fixture description","hasAdultContent":false,"hasBoothShop":true,"isAcceptingRequest":false,"isFollowed":true,"isStopped":false,"isSupported":true,"profileItems":[{"id":"1","imageUrl":"https://example.invalid/item.png","thumbnailUrl":"https://example.invalid/item-thumb.png","type":"image","serviceProvider":null,"videoId":null},{"id":"2","imageUrl":null,"thumbnailUrl":null,"type":"video","serviceProvider":"youtube","videoId":"fixture"},{"id":"3","imageUrl":null,"thumbnailUrl":null,"type":"fixture","serviceProvider":null,"videoId":null}],"profileLinks":["https://twitter.com/fixture"],"user":$USER}"""

    private const val CREATOR_DETAIL = """{"body":$CREATOR}"""

    private const val CREATORS = """{"body":{"creators":[$CREATOR]}}"""

    private const val SEARCHED_CREATORS = """{"body":{"count":1,"creators":[$CREATOR],"nextPage":2}}"""

    private const val PLAN =
        """{"coverImageUrl":"https://example.invalid/plan.png","creatorId":"fixture","description":"Fixture plan","fee":500,"hasAdultContent":false,"id":"93000001","paymentMethod":"paypal","title":"Fixture plan","user":$USER}"""

    private const val PLANS = """{"body":{"plans":[$PLAN]}}"""

    private const val PLAN_DETAIL =
        """{"body":{"plan":$PLAN,"supportStartDatetime":"2026-01-01T00:00:00+09:00","supportTransactions":[{"id":"95000001","paidAmount":500,"supporter":{"iconUrl":null,"name":"Fixture Supporter","userId":"91000009"},"targetMonth":"2026-01","transactionDatetime":"2026-01-01T00:00:00+09:00"}],"supporterCardImageUrl":"https://example.invalid/card.png"}}"""

    private const val FEATURED_TAGS = """{"body":{"featuredTags":[{"count":3,"coverImageUrl":null,"tag":"fixture"}]}}"""

    private const val SEARCHED_TAGS = """{"body":[{"count":3,"value":"fixture"}]}"""

    private const val PAID_RECORDS =
        """{"body":{"payments":[{"creator":{"creatorId":"fixture","user":$USER},"id":"96000001","paidAmount":500,"paymentDatetime":"2026-01-01T00:00:00+09:00","paymentMethod":"paypal"}]}}"""

    private const val NEWSLETTERS =
        """{"body":[{"body":"Fixture newsletter","createdAt":"2026-01-01T00:00:00+09:00","creator":{"creatorId":"fixture","user":$USER},"id":"97000001","isRead":false}]}"""

    // One bell of each kind, since each is a separate subtype on the wire.
    private const val BELLS =
        """{"body":{"items":[""" +
            """{"count":null,"creatorId":null,"creatorUserId":null,"id":"98000001","isRootComment":null,"isUnread":true,"notifiedDatetime":"2026-01-01T00:00:00+09:00","post":$POST,"postCommentBody":null,"postId":null,"postTitle":null,"type":"on_post_published","userName":null,"userProfileImg":null},""" +
            """{"count":null,"creatorId":"fixture","creatorUserId":"91000001","id":"98000002","isRootComment":true,"isUnread":false,"notifiedDatetime":"2026-01-01T00:00:00+09:00","post":null,"postCommentBody":"Fixture comment","postId":"10000001","postTitle":"Fixture post","type":"post_comment","userName":"Fixture User","userProfileImg":"https://example.invalid/icon.png"},""" +
            """{"count":2,"creatorId":"fixture","creatorUserId":"91000001","id":"98000003","isRootComment":null,"isUnread":false,"notifiedDatetime":"2026-01-01T00:00:00+09:00","post":null,"postCommentBody":"Fixture comment","postId":"10000001","postTitle":null,"type":"post_comment_like","userName":null,"userProfileImg":null}""" +
            """],"nextUrl":"https://api.fanbox.cc/bell.list?page=2"}}"""

    private const val HOMEPAGE =
        """<html><head><meta name="metadata" content="{&quot;apiUrl&quot;:&quot;https://api.fanbox.cc&quot;,&quot;context&quot;:{&quot;privacyPolicy&quot;:{&quot;policyUrl&quot;:&quot;https://example.invalid/privacy&quot;,&quot;revisionHistoryUrl&quot;:&quot;https://example.invalid/privacy/history&quot;,&quot;shouldShowNotice&quot;:false,&quot;updateDate&quot;:&quot;2026-01-01&quot;},&quot;user&quot;:{&quot;creatorId&quot;:null,&quot;fanboxUserStatus&quot;:0,&quot;hasAdultContent&quot;:null,&quot;hasUnpaidPayments&quot;:false,&quot;iconUrl&quot;:null,&quot;isCreator&quot;:false,&quot;isMailAddressOutdated&quot;:false,&quot;isSupporter&quot;:false,&quot;lang&quot;:&quot;ja&quot;,&quot;name&quot;:&quot;Fixture User&quot;,&quot;planCount&quot;:0,&quot;showAdultContent&quot;:false,&quot;userId&quot;:&quot;91000001&quot;}},&quot;csrfToken&quot;:&quot;fixture-token&quot;}"></head><body></body></html>"""

    fun buildRequests(service: FanboxGuestService): List<RequestDescriptor> = listOf(
        service.buildHomePostsRequest(cursor),
        service.buildSupportingPostsRequest(cursor),
        service.buildCreatorPostsRequest("fixture", cursor),
        service.buildCreatorPostsPaginationRequest("fixture"),
        service.buildPostDetailRequest("10000001"),
        service.buildPostCommentsRequest("10000001", 10),
        service.buildTaggedPostsRequest(
            tag = "fixture",
            creatorId = "fixture",
            page = 2,
        ),
        service.buildLikePostRequest("10000001"),
        service.buildLikeCommentRequest("92000001"),
        service.buildAddCommentRequest(
            postId = "10000001",
            rootCommentId = "92000001",
            parentCommentId = "92000001",
            comment = "Fixture comment",
        ),
        service.buildDeleteCommentRequest("92000001"),
        service.buildCreatorDetailRequest("fixture"),
        service.buildFollowingCreatorsRequest(),
        service.buildFollowingPixivCreatorsRequest(),
        service.buildRecommendedCreatorsRequest(),
        service.buildCreatorPlansRequest("fixture"),
        service.buildCreatorPlanDetailRequest("fixture"),
        service.buildCreatorTagsRequest("fixture"),
        service.buildFollowCreatorRequest(91000001),
        service.buildUnfollowCreatorRequest(91000001),
        service.buildSearchCreatorsRequest("fixture", 2),
        service.buildSearchTagsRequest("fixture"),
        service.buildSupportingPlansRequest(),
        service.buildPaidRecordsRequest(),
        service.buildUnpaidRecordsRequest(),
        service.buildNewslettersRequest(),
        service.buildBellsRequest(page = 2, markNotificationsRead = true),
        service.buildHomepageRequest(),
    )

    fun parseResponses(service: FanboxGuestService): List<GuestParseResult<*>> = listOf(
        service.parseHomePosts(POST_LIST, 200),
        service.parseSupportingPosts(POST_LIST, 200),
        service.parseCreatorPosts(
            body = CREATOR_POSTS,
            statusCode = 200,
            nextCursor = cursor,
        ),
        service.parseCreatorPostsPagination(PAGINATION, 200),
        service.parsePostDetail(POST_DETAIL, 200),
        service.parsePostComments(COMMENTS, 200),
        service.parseTaggedPosts(TAGGED_POSTS, 200),
        service.parseCreatorDetail(CREATOR_DETAIL, 200),
        service.parseFollowingCreators(CREATORS, 200),
        service.parseFollowingPixivCreators(CREATORS, 200),
        service.parseRecommendedCreators(CREATORS, 200),
        service.parseCreatorPlans(PLANS, 200),
        service.parseCreatorPlanDetail(PLAN_DETAIL, 200),
        service.parseCreatorTags(FEATURED_TAGS, 200),
        service.parseSearchCreators(SEARCHED_CREATORS, 200),
        service.parseSearchTags(SEARCHED_TAGS, 200),
        service.parseSupportingPlansStrict(PLANS, 200),
        service.parseSupportingPlansTolerant(PLANS, 200),
        service.parsePaidRecords(PAID_RECORDS, 200),
        service.parseUnpaidRecords(PAID_RECORDS, 200),
        service.parseNewsletters(NEWSLETTERS, 200),
        service.parseBells(BELLS, 200),
        service.parseHomepage(HOMEPAGE, 200),
    )

    fun parseRejectedResponse(service: FanboxGuestService): GuestParseResult<*> = service.parsePostDetail("""{"body":{"unexpected":true}}""", 200)
}
