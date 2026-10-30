package me.matsumo.fankt.fanbox.guest

import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.FanboxTolerantResult
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.PageCursorInfo
import me.matsumo.fankt.fanbox.domain.PageNumberInfo
import me.matsumo.fankt.fanbox.domain.PageOffsetInfo
import me.matsumo.fankt.fanbox.domain.model.FanboxComment
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlanDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxPost
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxTag
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.response.FanboxResponses

/** guest bundle が bind する [FanboxGuestService] の実装。FanboxEndpoints と FanboxResponses を bridge へ載せる。 */
// Mirrors FanboxGuestService, which declares one function per operation.
@Suppress("TooManyFunctions")
internal class RealFanboxGuestService : FanboxGuestService {

    override fun buildHomePostsRequest(cursor: FanboxCursor?): RequestDescriptor = FanboxEndpoints.homePosts(cursor)

    override fun parseHomePosts(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<PageCursorInfo<FanboxPost>>> = parse { FanboxResponses.homePosts(body, statusCode) }

    override fun buildSupportingPostsRequest(cursor: FanboxCursor?): RequestDescriptor = FanboxEndpoints.supportingPosts(cursor)

    override fun parseSupportingPosts(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<PageCursorInfo<FanboxPost>>> = parse { FanboxResponses.supportingPosts(body, statusCode) }

    override fun buildCreatorPostsRequest(creatorId: String, cursor: FanboxCursor): RequestDescriptor = FanboxEndpoints.creatorPosts(FanboxCreatorId(creatorId), cursor)

    override fun parseCreatorPosts(
        body: String,
        statusCode: Int,
        nextCursor: FanboxCursor?,
    ): GuestParseResult<FanboxTolerantResult<PageCursorInfo<FanboxPost>>> = parse {
        FanboxResponses.creatorPosts(
            body = body,
            nextCursor = nextCursor,
            statusCode = statusCode,
        )
    }

    override fun buildCreatorPostsPaginationRequest(creatorId: String): RequestDescriptor = FanboxEndpoints.creatorPostsPagination(FanboxCreatorId(creatorId))

    override fun parseCreatorPostsPagination(body: String, statusCode: Int): GuestParseResult<List<FanboxCursor>> = parse { FanboxResponses.creatorPostsPagination(body, statusCode) }

    override fun buildPostDetailRequest(postId: String): RequestDescriptor = FanboxEndpoints.postDetail(FanboxPostId(postId))

    override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult<FanboxPostDetail> = parse { FanboxResponses.postDetail(body, statusCode) }

    override fun buildPostCommentsRequest(postId: String, offset: Int): RequestDescriptor = FanboxEndpoints.postComments(FanboxPostId(postId), offset)

    override fun parsePostComments(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<PageOffsetInfo<FanboxComment>>> = parse { FanboxResponses.postComments(body, statusCode) }

    override fun buildTaggedPostsRequest(
        tag: String,
        creatorId: String?,
        page: Int,
    ): RequestDescriptor = FanboxEndpoints.taggedPosts(
        tag = tag,
        creatorId = creatorId?.let(::FanboxCreatorId),
        page = page,
    )

    override fun parseTaggedPosts(body: String, statusCode: Int): GuestParseResult<PageNumberInfo<FanboxPost>> = parse { FanboxResponses.taggedPosts(body, statusCode) }

    override fun buildLikePostRequest(postId: String): RequestDescriptor = FanboxEndpoints.likePost(FanboxPostId(postId))

    override fun buildLikeCommentRequest(commentId: String): RequestDescriptor = FanboxEndpoints.likeComment(FanboxCommentId(commentId))

    override fun buildAddCommentRequest(
        postId: String,
        rootCommentId: String?,
        parentCommentId: String?,
        comment: String,
    ): RequestDescriptor = FanboxEndpoints.addComment(
        postId = FanboxPostId(postId),
        rootCommentId = rootCommentId?.let(::FanboxCommentId),
        parentCommentId = parentCommentId?.let(::FanboxCommentId),
        body = comment,
    )

    override fun buildDeleteCommentRequest(commentId: String): RequestDescriptor = FanboxEndpoints.deleteComment(FanboxCommentId(commentId))

    override fun buildCreatorDetailRequest(creatorId: String): RequestDescriptor = FanboxEndpoints.creatorDetail(FanboxCreatorId(creatorId))

    override fun parseCreatorDetail(body: String, statusCode: Int): GuestParseResult<FanboxCreatorDetail> = parse {
        FanboxResponses.creatorDetail(body, statusCode)
    }

    override fun buildFollowingCreatorsRequest(): RequestDescriptor = FanboxEndpoints.followingCreators()

    override fun parseFollowingCreators(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<List<FanboxCreatorDetail>>> = parse {
        FanboxResponses.followingCreators(body, statusCode)
    }

    override fun buildFollowingPixivCreatorsRequest(): RequestDescriptor = FanboxEndpoints.followingPixivCreators()

    override fun parseFollowingPixivCreators(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<List<FanboxCreatorDetail>>> = parse {
        FanboxResponses.followingPixivCreators(body, statusCode)
    }

    override fun buildRecommendedCreatorsRequest(): RequestDescriptor = FanboxEndpoints.recommendedCreators()

    override fun parseRecommendedCreators(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<List<FanboxCreatorDetail>>> = parse {
        FanboxResponses.recommendedCreators(body, statusCode)
    }

    override fun buildCreatorPlansRequest(creatorId: String): RequestDescriptor = FanboxEndpoints.creatorPlans(FanboxCreatorId(creatorId))

    override fun parseCreatorPlans(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<List<FanboxCreatorPlan>>> = parse {
        FanboxResponses.creatorPlans(body, statusCode)
    }

    override fun buildCreatorPlanDetailRequest(creatorId: String): RequestDescriptor = FanboxEndpoints.creatorPlanDetail(FanboxCreatorId(creatorId))

    override fun parseCreatorPlanDetail(body: String, statusCode: Int): GuestParseResult<FanboxCreatorPlanDetail> = parse {
        FanboxResponses.creatorPlanDetail(body, statusCode)
    }

    override fun buildCreatorTagsRequest(creatorId: String): RequestDescriptor = FanboxEndpoints.creatorTags(FanboxCreatorId(creatorId))

    override fun parseCreatorTags(body: String, statusCode: Int): GuestParseResult<List<FanboxTag>> = parse {
        FanboxResponses.creatorTags(body, statusCode)
    }

    override fun buildFollowCreatorRequest(userId: Long): RequestDescriptor = FanboxEndpoints.followCreator(FanboxUserId(userId))

    override fun buildUnfollowCreatorRequest(userId: Long): RequestDescriptor = FanboxEndpoints.unfollowCreator(FanboxUserId(userId))

    override fun buildSearchCreatorsRequest(query: String, page: Int): RequestDescriptor = FanboxEndpoints.searchCreators(query, page)

    override fun parseSearchCreators(body: String, statusCode: Int): GuestParseResult<PageNumberInfo<FanboxCreatorDetail>> = parse {
        FanboxResponses.searchCreators(body, statusCode)
    }

    override fun buildSearchTagsRequest(query: String): RequestDescriptor = FanboxEndpoints.searchTags(query)

    override fun parseSearchTags(body: String, statusCode: Int): GuestParseResult<List<FanboxTag>> = parse {
        FanboxResponses.searchTags(body, statusCode)
    }

    // Throwable rather than RuntimeException: a Kotlin/JS engine failure need not be one.
    private inline fun <T> parse(block: () -> T): GuestParseResult<T> = try {
        GuestParseResult.Success(block())
    } catch (failure: FanboxException) {
        GuestParseResult.SchemaMismatch(failure.describe())
    } catch (failure: Throwable) {
        GuestParseResult.GuestFailure(failure.describe())
    }

    private fun Throwable.describe(): String = message?.takeIf(String::isNotBlank) ?: this::class.simpleName.orEmpty()
}
