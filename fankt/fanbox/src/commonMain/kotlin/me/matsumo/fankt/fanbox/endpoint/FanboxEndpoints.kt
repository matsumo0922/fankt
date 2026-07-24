package me.matsumo.fankt.fanbox.endpoint

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId

internal object FanboxEndpoints {
    private const val DEFAULT_LOAD_SIZE = "20"

    fun homePosts(cursor: FanboxCursor?): RequestDescriptor = pagedPosts(
        endpointId = FanboxEndpointIds.postListHome,
        path = "post.listHome",
        cursor = cursor,
    )

    fun supportingPosts(cursor: FanboxCursor?): RequestDescriptor = pagedPosts(
        endpointId = FanboxEndpointIds.postListSupporting,
        path = "post.listSupporting",
        cursor = cursor,
    )

    fun creatorPosts(creatorId: FanboxCreatorId, cursor: FanboxCursor): RequestDescriptor = get(
        FanboxEndpointIds.postListCreator,
        "post.listCreator",
        query(
            "creatorId" to creatorId.value,
            "limit" to (cursor.limit?.toString() ?: DEFAULT_LOAD_SIZE),
            "firstPublishedDatetime" to cursor.firstPublishedDatetime,
            "maxPublishedDatetime" to cursor.maxPublishedDatetime,
            "firstId" to cursor.firstId,
            "maxId" to cursor.maxId,
        ),
    )

    fun creatorPostsPagination(creatorId: FanboxCreatorId): RequestDescriptor = get(
        FanboxEndpointIds.postPaginateCreator,
        "post.paginateCreator",
        query("creatorId" to creatorId.value),
    )

    fun postDetail(postId: FanboxPostId): RequestDescriptor = get(
        FanboxEndpointIds.postInfo,
        "post.info",
        query("postId" to postId.value),
    )

    fun postComments(postId: FanboxPostId, offset: Int, loadSize: String = DEFAULT_LOAD_SIZE): RequestDescriptor = get(
        FanboxEndpointIds.postGetComments,
        "post.getComments",
        query(
            "postId" to postId.value,
            "offset" to offset.toString(),
            "limit" to loadSize,
        ),
    )

    fun taggedPosts(tag: String, creatorId: FanboxCreatorId?, page: Int = 0): RequestDescriptor = get(
        FanboxEndpointIds.postListTagged,
        "post.listTagged",
        query(
            "tag" to tag,
            "creatorId" to creatorId?.value,
            "page" to page.toString(),
        ),
    )

    fun likePost(postId: FanboxPostId): RequestDescriptor = post(
        FanboxEndpointIds.postLikePost,
        "post.likePost",
        buildJsonObject { put("postId", postId.value) }.toString(),
    )

    fun likeComment(commentId: FanboxCommentId): RequestDescriptor = post(
        FanboxEndpointIds.postLikeComment,
        "post.likeComment",
        buildJsonObject { put("commentId", commentId.value) }.toString(),
    )

    fun addComment(
        postId: FanboxPostId,
        rootCommentId: FanboxCommentId?,
        parentCommentId: FanboxCommentId?,
        body: String,
    ): RequestDescriptor = post(
        FanboxEndpointIds.postAddComment,
        "post.addComment",
        buildJsonObject {
            put("postId", postId.value)
            rootCommentId?.let { put("rootCommentId", it.value) }
            parentCommentId?.let { put("parentCommentId", it.value) }
            put("body", body)
        }.toString(),
    )

    fun deleteComment(commentId: FanboxCommentId): RequestDescriptor = post(
        FanboxEndpointIds.postDeleteComment,
        "post.deleteComment",
        buildJsonObject { put("commentId", commentId.value) }.toString(),
    )

    fun creatorDetail(creatorId: FanboxCreatorId): RequestDescriptor = get(
        FanboxEndpointIds.creatorGet,
        "creator.get",
        query("creatorId" to creatorId.value),
    )

    fun followingCreators(): RequestDescriptor = get(
        FanboxEndpointIds.creatorListFollowing,
        "creator.listFollowing",
    )

    fun followingPixivCreators(): RequestDescriptor = get(
        FanboxEndpointIds.creatorListPixiv,
        "creator.listPixiv",
    )

    fun recommendedCreators(loadSize: String = DEFAULT_LOAD_SIZE): RequestDescriptor = get(
        FanboxEndpointIds.creatorListRecommended,
        "creator.listRecommended",
        query("limit" to loadSize),
    )

    fun creatorPlans(creatorId: FanboxCreatorId): RequestDescriptor = get(
        FanboxEndpointIds.planListCreator,
        "plan.listCreator",
        query("creatorId" to creatorId.value),
    )

    fun creatorPlanDetail(creatorId: FanboxCreatorId): RequestDescriptor = get(
        FanboxEndpointIds.legacySupportCreator,
        "legacy/support/creator",
        query("creatorId" to creatorId.value),
    )

    fun creatorTags(creatorId: FanboxCreatorId): RequestDescriptor = get(
        FanboxEndpointIds.tagGetFeatured,
        "tag.getFeatured",
        query("creatorId" to creatorId.value),
    )

    fun followCreator(userId: FanboxUserId): RequestDescriptor = post(
        FanboxEndpointIds.followCreate,
        "follow.create",
        buildJsonObject { put("creatorUserId", userId.value.toString()) }.toString(),
    )

    fun unfollowCreator(userId: FanboxUserId): RequestDescriptor = post(
        FanboxEndpointIds.followDelete,
        "follow.delete",
        buildJsonObject { put("creatorUserId", userId.value.toString()) }.toString(),
    )

    fun searchCreators(query: String, page: Int = 0): RequestDescriptor = get(
        FanboxEndpointIds.creatorSearch,
        "creator.search",
        query("q" to query, "page" to page.toString()),
    )

    fun searchTags(query: String): RequestDescriptor = get(
        FanboxEndpointIds.tagSearch,
        "tag.search",
        query("q" to query),
    )

    fun supportingPlans(): RequestDescriptor = get(
        FanboxEndpointIds.planListSupporting,
        "plan.listSupporting",
    )

    fun paidRecords(): RequestDescriptor = get(
        FanboxEndpointIds.paymentListPaid,
        "payment.listPaid",
    )

    fun unpaidRecords(): RequestDescriptor = get(
        FanboxEndpointIds.paymentListUnpaid,
        "payment.listUnpaid",
    )

    fun newsletters(): RequestDescriptor = get(
        FanboxEndpointIds.newsletterList,
        "newsletter.list",
    )

    fun bells(
        page: Int = 0,
        skipConvertUnreadNotification: Int = 0,
        commentOnly: Int = 0,
    ): RequestDescriptor = get(
        FanboxEndpointIds.bellList,
        "bell.list",
        query(
            "page" to page.toString(),
            "skipConvertUnreadNotification" to skipConvertUnreadNotification.toString(),
            "commentOnly" to commentOnly.toString(),
        ),
    )

    fun homepage(): RequestDescriptor = get(FanboxEndpointIds.homepage, "")

    private fun pagedPosts(
        endpointId: FanboxEndpointId,
        path: String,
        cursor: FanboxCursor?,
    ): RequestDescriptor = get(
        endpointId,
        path,
        query(
            "limit" to (cursor?.limit?.toString() ?: DEFAULT_LOAD_SIZE),
            "firstPublishedDatetime" to cursor?.firstPublishedDatetime,
            "maxPublishedDatetime" to cursor?.maxPublishedDatetime,
            "firstId" to cursor?.firstId,
            "maxId" to cursor?.maxId,
        ),
    )

    private fun get(
        endpointId: FanboxEndpointId,
        path: String,
        query: List<FanboxQueryParameter> = emptyList(),
    ) = RequestDescriptor(endpointId, path, FanboxHttpMethod.GET, query)

    private fun post(endpointId: FanboxEndpointId, path: String, jsonBody: String) = RequestDescriptor(
        endpointId = endpointId,
        path = path,
        method = FanboxHttpMethod.POST,
        jsonBody = jsonBody,
    )

    private fun query(vararg values: Pair<String, String?>): List<FanboxQueryParameter> =
        values.mapNotNull { (name, value) -> value?.let { FanboxQueryParameter(name, it) } }
}
