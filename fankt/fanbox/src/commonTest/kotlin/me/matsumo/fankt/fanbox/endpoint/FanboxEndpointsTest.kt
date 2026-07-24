package me.matsumo.fankt.fanbox.endpoint

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.response.fanboxResponseOperations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FanboxEndpointsTest {

    @Test
    fun inventoriesKeepTwentyNineOperationsAndTwentyEightUniqueRoutes() {
        assertEquals(29, fanboxRequestOperations.size)
        assertEquals(28, fanboxUniqueRequestRoutes.size)
        assertEquals(29, fanboxResponseOperations.size)
        assertEquals(
            listOf(
                "plan.listSupporting.strict",
                "plan.listSupporting.tolerant",
            ),
            fanboxRequestOperations
                .filter { it.endpointId == FanboxEndpointIds.planListSupporting }
                .map { it.name },
        )
        assertEquals(
            setOf(FanboxEndpointIds.planListSupporting),
            fanboxRequestOperations.groupBy { it.endpointId }.filterValues { it.size > 1 }.keys,
        )
    }

    @Test
    fun everyUniqueRouteBuilderMatchesCurrentPathAndMethod() {
        val cursor = FanboxCursor("first", "max", "first-id", "max-id", 30)
        val creatorId = FanboxCreatorId("creator")
        val postId = FanboxPostId("post")
        val commentId = FanboxCommentId("comment")
        val userId = FanboxUserId(123)
        val descriptors = listOf(
            FanboxEndpoints.homePosts(cursor),
            FanboxEndpoints.supportingPosts(cursor),
            FanboxEndpoints.creatorPosts(creatorId, cursor),
            FanboxEndpoints.creatorPostsPagination(creatorId),
            FanboxEndpoints.postDetail(postId),
            FanboxEndpoints.postComments(postId, 20),
            FanboxEndpoints.taggedPosts("tag", creatorId, 2),
            FanboxEndpoints.likePost(postId),
            FanboxEndpoints.likeComment(commentId),
            FanboxEndpoints.addComment(postId, commentId, commentId, "body"),
            FanboxEndpoints.deleteComment(commentId),
            FanboxEndpoints.creatorDetail(creatorId),
            FanboxEndpoints.followingCreators(),
            FanboxEndpoints.followingPixivCreators(),
            FanboxEndpoints.recommendedCreators(),
            FanboxEndpoints.creatorPlans(creatorId),
            FanboxEndpoints.creatorPlanDetail(creatorId),
            FanboxEndpoints.creatorTags(creatorId),
            FanboxEndpoints.followCreator(userId),
            FanboxEndpoints.unfollowCreator(userId),
            FanboxEndpoints.searchCreators("query", 2),
            FanboxEndpoints.searchTags("query"),
            FanboxEndpoints.supportingPlans(),
            FanboxEndpoints.paidRecords(),
            FanboxEndpoints.unpaidRecords(),
            FanboxEndpoints.newsletters(),
            FanboxEndpoints.bells(2),
            FanboxEndpoints.homepage(),
        )

        assertEquals(fanboxUniqueRequestRoutes, descriptors.mapTo(linkedSetOf()) { it.endpointId })
        assertEquals(
            fanboxUniqueRequestRoutes - FanboxEndpointIds.homepage,
            descriptors.filter { it.path.isNotEmpty() }.mapTo(linkedSetOf()) { it.endpointId },
        )
        assertEquals("", descriptors.single { it.endpointId == FanboxEndpointIds.homepage }.path)
        assertEquals(
            setOf(
                "post.likePost",
                "post.likeComment",
                "post.addComment",
                "post.deleteComment",
                "follow.create",
                "follow.delete",
            ),
            descriptors.filter { it.method == FanboxHttpMethod.POST }.mapTo(linkedSetOf()) { it.path },
        )
        assertEquals(
            descriptors.map { it.endpointId.value }.toSet() - "homepage",
            descriptors.map { it.path }.filter(String::isNotEmpty).toSet(),
        )
        val expectedQueryNames = mapOf(
            FanboxEndpointIds.postListHome to listOf(
                "limit",
                "firstPublishedDatetime",
                "maxPublishedDatetime",
                "firstId",
                "maxId",
            ),
            FanboxEndpointIds.postListSupporting to listOf(
                "limit",
                "firstPublishedDatetime",
                "maxPublishedDatetime",
                "firstId",
                "maxId",
            ),
            FanboxEndpointIds.postListCreator to listOf(
                "creatorId",
                "limit",
                "firstPublishedDatetime",
                "maxPublishedDatetime",
                "firstId",
                "maxId",
            ),
            FanboxEndpointIds.postPaginateCreator to listOf("creatorId"),
            FanboxEndpointIds.postInfo to listOf("postId"),
            FanboxEndpointIds.postGetComments to listOf("postId", "offset", "limit"),
            FanboxEndpointIds.postListTagged to listOf("tag", "creatorId", "page"),
            FanboxEndpointIds.creatorGet to listOf("creatorId"),
            FanboxEndpointIds.creatorListRecommended to listOf("limit"),
            FanboxEndpointIds.planListCreator to listOf("creatorId"),
            FanboxEndpointIds.legacySupportCreator to listOf("creatorId"),
            FanboxEndpointIds.tagGetFeatured to listOf("creatorId"),
            FanboxEndpointIds.creatorSearch to listOf("q", "page"),
            FanboxEndpointIds.tagSearch to listOf("q"),
            FanboxEndpointIds.bellList to listOf("page", "skipConvertUnreadNotification", "commentOnly"),
        )
        descriptors.forEach { descriptor ->
            assertEquals(
                expectedQueryNames[descriptor.endpointId].orEmpty(),
                descriptor.query.map { it.name },
                descriptor.endpointId.value,
            )
        }
    }

    @Test
    fun paginationQueriesAreOrderedUnencodedAndOmitNullValues() {
        val descriptor = FanboxEndpoints.homePosts(
            FanboxCursor(
                firstPublishedDatetime = "2000-01-01T00:00:00+00:00&next=true",
                maxPublishedDatetime = null,
                firstId = "id=value",
                maxId = null,
                limit = null,
            ),
        )

        assertEquals(
            listOf(
                FanboxQueryParameter("limit", "20"),
                FanboxQueryParameter("firstPublishedDatetime", "2000-01-01T00:00:00+00:00&next=true"),
                FanboxQueryParameter("firstId", "id=value"),
            ),
            descriptor.query,
        )
        assertNull(descriptor.jsonBody)
    }

    @Test
    fun postBodiesPreserveStringTypesAndNullableParentOmission() {
        val root = FanboxEndpoints.addComment(
            postId = FanboxPostId("post-id"),
            rootCommentId = null,
            parentCommentId = null,
            body = "root",
        ).jsonBody.orEmpty().let(Json::parseToJsonElement).jsonObject
        val reply = FanboxEndpoints.addComment(
            postId = FanboxPostId("post-id"),
            rootCommentId = FanboxCommentId("root-id"),
            parentCommentId = FanboxCommentId("parent-id"),
            body = "reply",
        ).jsonBody.orEmpty().let(Json::parseToJsonElement).jsonObject
        val follow = FanboxEndpoints.followCreator(FanboxUserId(1234567890123))
            .jsonBody.orEmpty().let(Json::parseToJsonElement).jsonObject
        val unfollow = FanboxEndpoints.unfollowCreator(FanboxUserId(1234567890123))
            .jsonBody.orEmpty().let(Json::parseToJsonElement).jsonObject
        val postLike = FanboxEndpoints.likePost(FanboxPostId("post-id"))
            .jsonBody.orEmpty().let(Json::parseToJsonElement).jsonObject
        val commentLike = FanboxEndpoints.likeComment(FanboxCommentId("comment-id"))
            .jsonBody.orEmpty().let(Json::parseToJsonElement).jsonObject
        val commentDelete = FanboxEndpoints.deleteComment(FanboxCommentId("comment-id"))
            .jsonBody.orEmpty().let(Json::parseToJsonElement).jsonObject

        assertFalse("rootCommentId" in root)
        assertFalse("parentCommentId" in root)
        assertEquals(JsonPrimitive("root-id"), reply.getValue("rootCommentId"))
        assertEquals(JsonPrimitive("parent-id"), reply.getValue("parentCommentId"))
        assertEquals(JsonPrimitive("1234567890123"), follow.getValue("creatorUserId"))
        assertEquals(JsonPrimitive("1234567890123"), unfollow.getValue("creatorUserId"))
        assertEquals(JsonPrimitive("post-id"), postLike.getValue("postId"))
        assertEquals(JsonPrimitive("comment-id"), commentLike.getValue("commentId"))
        assertEquals(JsonPrimitive("comment-id"), commentDelete.getValue("commentId"))
    }

    @Test
    fun descriptorContractIsSerializableWithoutPublicAbiExposure() {
        val descriptor = FanboxEndpoints.postDetail(FanboxPostId("post-id"))
        val encoded = Json.encodeToString(descriptor)

        assertEquals(descriptor, Json.decodeFromString<RequestDescriptor>(encoded))
    }
}
