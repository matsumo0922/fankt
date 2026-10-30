package me.matsumo.fankt.fanbox.guest

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.matsumo.fankt.fanbox.createFanboxJson
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxTolerantListJsonFixtures

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
        val home = createFanboxJson().parseToJsonElement(FanboxPostJsonFixtures.postListHomeNormal).jsonObject.getValue("body").jsonObject

        put(
            "body",
            buildJsonObject {
                put("count", 2)
                put("items", home.getValue("items"))
                put("nextUrl", "https://api.fanbox.cc/post.listTagged?tag=fixture&page=2")
            },
        )
    }.toString()

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
        )
    }
}
