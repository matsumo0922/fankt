package me.matsumo.fankt.fanbox.guest

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.matsumo.fankt.fanbox.createFanboxJson
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.fixture.FanboxCreatorJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxMetadataHtmlFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxPaymentJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxTolerantListJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxUserJsonFixtures

/**
 * guest の全 operation を fixture で呼び出す。
 *
 * native の guest と bridge 越しの guest で同じ呼び出しを行い、結果を比べるテストが共有する。
 */
internal object GuestOperationCases {
    val cursor = FanboxCursor(
        firstPublishedDatetime = "2026-01-01 00:00:00",
        maxPublishedDatetime = "2026-02-01 00:00:00",
        firstId = "100",
        maxId = "200",
        limit = 10,
    )

    // post.listTagged has no captured fixture, so the home timeline's items are reused in its envelope.
    val taggedPosts = buildJsonObject {
        val home = fixtureBody(FanboxPostJsonFixtures.postListHomeNormal).jsonObject

        put(
            "body",
            buildJsonObject {
                put("count", 2)
                put("items", home.getValue("items"))
                put("nextUrl", "https://api.fanbox.cc/post.listTagged?tag=fixture&page=2")
            },
        )
    }.toString()

    // The creator.get fixture's creator reused in the search envelope, which has no captured fixture.
    val searchedCreators = buildJsonObject {
        put(
            "body",
            buildJsonObject {
                put("count", 1)
                put("creators", JsonArray(listOf(fixtureBody(FanboxCreatorJsonFixtures.actualCreatorGet))))
                put("nextPage", 2)
            },
        )
    }.toString()

    // The first plan of the plan.listCreator fixture, as legacy/support/creator returns it for a supporter.
    val supportedPlanDetail = buildJsonObject {
        put(
            "body",
            buildJsonObject {
                put("plan", fixtureBody(FanboxCreatorJsonFixtures.actualCreatorPlanList).jsonObject.getValue("plans").jsonArray.first())
                put("supportStartDatetime", "2026-01-01T00:00:00+09:00")
                put(
                    "supportTransactions",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", "95000001")
                                put("paidAmount", 1234)
                                put(
                                    "supporter",
                                    buildJsonObject {
                                        put("iconUrl", null as String?)
                                        put("name", "Fixture Supporter")
                                        put("userId", "91000009")
                                    },
                                )
                                put("targetMonth", "2026-01")
                                put("transactionDatetime", "2026-01-01T00:00:00+09:00")
                            },
                        )
                    },
                )
                put("supporterCardImageUrl", "https://example.invalid/supporter-card")
            },
        )
    }.toString()

    const val FEATURED_TAGS = """{"body":{"featuredTags":[{"count":3,"coverImageUrl":null,"tag":"fixture"}]}}"""

    const val SEARCHED_TAGS = """{"body":[{"count":3,"value":"fixture"}]}"""

    fun buildRequests(service: FanboxGuestService): List<RequestDescriptor> = listOf(
        service.buildHomePostsRequest(null),
        service.buildHomePostsRequest(cursor),
        service.buildSupportingPostsRequest(cursor),
        service.buildCreatorPostsRequest("creator", cursor),
        service.buildCreatorPostsPaginationRequest("creator"),
        service.buildPostDetailRequest("post"),
        service.buildPostCommentsRequest("post", 20),
        service.buildTaggedPostsRequest(
            tag = "fixture",
            creatorId = "creator",
            page = 2,
        ),
        service.buildTaggedPostsRequest(
            tag = "fixture",
            creatorId = null,
            page = 0,
        ),
        service.buildLikePostRequest("post"),
        service.buildLikeCommentRequest("comment"),
        service.buildAddCommentRequest(
            postId = "post",
            rootCommentId = "root",
            parentCommentId = "parent",
            comment = "fixture comment",
        ),
        service.buildAddCommentRequest(
            postId = "post",
            rootCommentId = null,
            parentCommentId = null,
            comment = "fixture comment",
        ),
        service.buildDeleteCommentRequest("comment"),
        service.buildCreatorDetailRequest("creator"),
        service.buildFollowingCreatorsRequest(),
        service.buildFollowingPixivCreatorsRequest(),
        service.buildRecommendedCreatorsRequest(),
        service.buildCreatorPlansRequest("creator"),
        service.buildCreatorPlanDetailRequest("creator"),
        service.buildCreatorTagsRequest("creator"),
        service.buildFollowCreatorRequest(91000001),
        service.buildUnfollowCreatorRequest(91000001),
        service.buildSearchCreatorsRequest("fixture", 2),
        service.buildSearchTagsRequest("fixture"),
        service.buildSupportingPlansRequest(),
        service.buildPaidRecordsRequest(),
        service.buildUnpaidRecordsRequest(),
        service.buildNewslettersRequest(),
        service.buildBellsRequest(page = 2, markNotificationsRead = true),
        service.buildBellsRequest(page = 0, markNotificationsRead = false),
        service.buildHomepageRequest(),
    )

    // The mixed timeline makes each list operation report a skipped item under its own endpoint.
    fun parseResponses(service: FanboxGuestService): List<GuestParseResult<*>> = listOf(
        service.parseHomePosts(FanboxTolerantListJsonFixtures.timelineMixed, 200),
        service.parseSupportingPosts(FanboxTolerantListJsonFixtures.timelineMixed, 200),
        service.parseCreatorPosts(
            body = FanboxPostJsonFixtures.postListCreatorNormal,
            statusCode = 200,
            nextCursor = cursor,
        ),
        service.parseCreatorPostsPagination(FanboxPostJsonFixtures.paginateCreatorNormal, 200),
        service.parsePostDetail(FanboxPostJsonFixtures.postInfoArticleEmbeds, 200),
        service.parsePostComments(FanboxTolerantListJsonFixtures.commentsWithBrokenReply, 200),
        service.parseTaggedPosts(taggedPosts, 200),
        service.parseCreatorDetail(FanboxCreatorJsonFixtures.actualCreatorGet, 200),
        service.parseFollowingCreators(FanboxTolerantListJsonFixtures.creatorsMixed, 200),
        service.parseFollowingPixivCreators(FanboxTolerantListJsonFixtures.creatorsMixed, 200),
        service.parseRecommendedCreators(FanboxTolerantListJsonFixtures.creatorsMixed, 200),
        service.parseCreatorPlans(FanboxCreatorJsonFixtures.actualCreatorPlanList, 200),
        service.parseCreatorPlanDetail(supportedPlanDetail, 200),
        service.parseCreatorTags(FEATURED_TAGS, 200),
        service.parseSearchCreators(searchedCreators, 200),
        service.parseSearchTags(SEARCHED_TAGS, 200),
        service.parseSupportingPlansStrict(FanboxCreatorJsonFixtures.actualCreatorPlanList, 200),
        service.parseSupportingPlansTolerant(FanboxTolerantListJsonFixtures.supportingPlansMixed, 200),
        service.parsePaidRecords(FanboxPaymentJsonFixtures.paidRecords, 200),
        service.parseUnpaidRecords(FanboxPaymentJsonFixtures.paidRecords, 200),
        service.parseNewsletters(FanboxUserJsonFixtures.handcraftedNewsletterList, 200),
        service.parseBells(FanboxTolerantListJsonFixtures.bellMixed, 200),
        service.parseHomepage(FanboxMetadataHtmlFixtures.home, 200),
    )

    fun parseRejectedResponses(service: FanboxGuestService): List<GuestParseResult<*>> {
        val body = """{"body":{"unexpected":true}}"""

        return listOf(
            service.parseHomePosts(body, 200),
            service.parseSupportingPosts(body, 200),
            service.parseCreatorPosts(
                body = body,
                statusCode = 200,
                nextCursor = null,
            ),
            service.parseCreatorPostsPagination(body, 200),
            service.parsePostDetail(body, 200),
            service.parsePostComments(body, 200),
            service.parseTaggedPosts(body, 200),
            service.parseCreatorDetail(body, 200),
            service.parseFollowingCreators(body, 200),
            service.parseFollowingPixivCreators(body, 200),
            service.parseRecommendedCreators(body, 200),
            service.parseCreatorPlans(body, 200),
            service.parseCreatorPlanDetail(body, 200),
            service.parseCreatorTags(body, 200),
            service.parseSearchCreators(body, 200),
            service.parseSearchTags(body, 200),
            service.parseSupportingPlansStrict(body, 200),
            service.parseSupportingPlansTolerant(body, 200),
            service.parsePaidRecords(body, 200),
            service.parseUnpaidRecords(body, 200),
            service.parseNewsletters(body, 200),
            service.parseBells(body, 200),
            service.parseHomepage(body, 200),
        )
    }

    private fun fixtureBody(fixture: String): JsonElement = createFanboxJson().parseToJsonElement(fixture).jsonObject.getValue("body")
}
