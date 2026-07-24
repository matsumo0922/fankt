package me.matsumo.fankt.fanbox.endpoint

internal object FanboxEndpointIds {
    val bellList = FanboxEndpointId("bell.list")
    val creatorGet = FanboxEndpointId("creator.get")
    val creatorListFollowing = FanboxEndpointId("creator.listFollowing")
    val creatorListPixiv = FanboxEndpointId("creator.listPixiv")
    val creatorListRecommended = FanboxEndpointId("creator.listRecommended")
    val creatorSearch = FanboxEndpointId("creator.search")
    val followCreate = FanboxEndpointId("follow.create")
    val followDelete = FanboxEndpointId("follow.delete")
    val homepage = FanboxEndpointId("homepage")
    val legacySupportCreator = FanboxEndpointId("legacy/support/creator")
    val newsletterList = FanboxEndpointId("newsletter.list")
    val paymentListPaid = FanboxEndpointId("payment.listPaid")
    val paymentListUnpaid = FanboxEndpointId("payment.listUnpaid")
    val planListCreator = FanboxEndpointId("plan.listCreator")
    val planListSupporting = FanboxEndpointId("plan.listSupporting")
    val postAddComment = FanboxEndpointId("post.addComment")
    val postDeleteComment = FanboxEndpointId("post.deleteComment")
    val postGetComments = FanboxEndpointId("post.getComments")
    val postInfo = FanboxEndpointId("post.info")
    val postLikeComment = FanboxEndpointId("post.likeComment")
    val postLikePost = FanboxEndpointId("post.likePost")
    val postListCreator = FanboxEndpointId("post.listCreator")
    val postListHome = FanboxEndpointId("post.listHome")
    val postListSupporting = FanboxEndpointId("post.listSupporting")
    val postListTagged = FanboxEndpointId("post.listTagged")
    val postPaginateCreator = FanboxEndpointId("post.paginateCreator")
    val tagGetFeatured = FanboxEndpointId("tag.getFeatured")
    val tagSearch = FanboxEndpointId("tag.search")
}

internal data class FanboxRequestOperation(
    val name: String,
    val endpointId: FanboxEndpointId,
)

internal val fanboxRequestOperations: List<FanboxRequestOperation> = listOf(
    FanboxRequestOperation("post.listHome", FanboxEndpointIds.postListHome),
    FanboxRequestOperation("post.listSupporting", FanboxEndpointIds.postListSupporting),
    FanboxRequestOperation("post.listCreator", FanboxEndpointIds.postListCreator),
    FanboxRequestOperation("post.paginateCreator", FanboxEndpointIds.postPaginateCreator),
    FanboxRequestOperation("post.info", FanboxEndpointIds.postInfo),
    FanboxRequestOperation("post.getComments", FanboxEndpointIds.postGetComments),
    FanboxRequestOperation("post.listTagged", FanboxEndpointIds.postListTagged),
    FanboxRequestOperation("post.likePost", FanboxEndpointIds.postLikePost),
    FanboxRequestOperation("post.likeComment", FanboxEndpointIds.postLikeComment),
    FanboxRequestOperation("post.addComment", FanboxEndpointIds.postAddComment),
    FanboxRequestOperation("post.deleteComment", FanboxEndpointIds.postDeleteComment),
    FanboxRequestOperation("creator.get", FanboxEndpointIds.creatorGet),
    FanboxRequestOperation("creator.listFollowing", FanboxEndpointIds.creatorListFollowing),
    FanboxRequestOperation("creator.listPixiv", FanboxEndpointIds.creatorListPixiv),
    FanboxRequestOperation("creator.listRecommended", FanboxEndpointIds.creatorListRecommended),
    FanboxRequestOperation("plan.listCreator", FanboxEndpointIds.planListCreator),
    FanboxRequestOperation("legacy/support/creator", FanboxEndpointIds.legacySupportCreator),
    FanboxRequestOperation("tag.getFeatured", FanboxEndpointIds.tagGetFeatured),
    FanboxRequestOperation("follow.create", FanboxEndpointIds.followCreate),
    FanboxRequestOperation("follow.delete", FanboxEndpointIds.followDelete),
    FanboxRequestOperation("creator.search", FanboxEndpointIds.creatorSearch),
    FanboxRequestOperation("tag.search", FanboxEndpointIds.tagSearch),
    FanboxRequestOperation("plan.listSupporting.strict", FanboxEndpointIds.planListSupporting),
    FanboxRequestOperation("plan.listSupporting.tolerant", FanboxEndpointIds.planListSupporting),
    FanboxRequestOperation("payment.listPaid", FanboxEndpointIds.paymentListPaid),
    FanboxRequestOperation("payment.listUnpaid", FanboxEndpointIds.paymentListUnpaid),
    FanboxRequestOperation("newsletter.list", FanboxEndpointIds.newsletterList),
    FanboxRequestOperation("bell.list", FanboxEndpointIds.bellList),
    FanboxRequestOperation("homepage", FanboxEndpointIds.homepage),
)

internal val fanboxUniqueRequestRoutes: Set<FanboxEndpointId> =
    fanboxRequestOperations.mapTo(linkedSetOf()) { it.endpointId }
