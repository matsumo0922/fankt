package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.ZiplineService
import kotlinx.serialization.Serializable
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
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor

/** The name the guest binds this service under and the host takes it by. */
internal const val FANBOX_GUEST_SERVICE_NAME: String = "fanboxGuest"

/**
 * Builds FANBOX requests and interprets FANBOX responses inside the Zipline guest, so that a
 * parsing fix reaches users by replacing the guest bundle rather than by shipping a new app.
 *
 * No parameter carries a credential. The host attaches cookies and the CSRF token after it
 * validates the descriptor this service returns. Functions that interpret no response body, such as
 * those for liking or commenting, only build the request.
 */
// One function per operation keeps every value that crosses the bridge typed.
@Suppress("TooManyFunctions")
internal interface FanboxGuestService : ZiplineService {
    fun buildHomePostsRequest(cursor: FanboxCursor?): RequestDescriptor

    fun parseHomePosts(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<PageCursorInfo<FanboxPost>>>

    fun buildSupportingPostsRequest(cursor: FanboxCursor?): RequestDescriptor

    fun parseSupportingPosts(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<PageCursorInfo<FanboxPost>>>

    fun buildCreatorPostsRequest(creatorId: String, cursor: FanboxCursor): RequestDescriptor

    fun parseCreatorPosts(
        body: String,
        statusCode: Int,
        nextCursor: FanboxCursor?,
    ): GuestParseResult<FanboxTolerantResult<PageCursorInfo<FanboxPost>>>

    fun buildCreatorPostsPaginationRequest(creatorId: String): RequestDescriptor

    fun parseCreatorPostsPagination(body: String, statusCode: Int): GuestParseResult<List<FanboxCursor>>

    fun buildPostDetailRequest(postId: String): RequestDescriptor

    fun parsePostDetail(body: String, statusCode: Int): GuestParseResult<FanboxPostDetail>

    fun buildPostCommentsRequest(postId: String, offset: Int): RequestDescriptor

    fun parsePostComments(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<PageOffsetInfo<FanboxComment>>>

    fun buildTaggedPostsRequest(
        tag: String,
        creatorId: String?,
        page: Int,
    ): RequestDescriptor

    fun parseTaggedPosts(body: String, statusCode: Int): GuestParseResult<PageNumberInfo<FanboxPost>>

    fun buildLikePostRequest(postId: String): RequestDescriptor

    fun buildLikeCommentRequest(commentId: String): RequestDescriptor

    fun buildAddCommentRequest(
        postId: String,
        rootCommentId: String?,
        parentCommentId: String?,
        comment: String,
    ): RequestDescriptor

    fun buildDeleteCommentRequest(commentId: String): RequestDescriptor

    fun buildCreatorDetailRequest(creatorId: String): RequestDescriptor

    fun parseCreatorDetail(body: String, statusCode: Int): GuestParseResult<FanboxCreatorDetail>

    fun buildFollowingCreatorsRequest(): RequestDescriptor

    fun parseFollowingCreators(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<List<FanboxCreatorDetail>>>

    fun buildFollowingPixivCreatorsRequest(): RequestDescriptor

    fun parseFollowingPixivCreators(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<List<FanboxCreatorDetail>>>

    fun buildRecommendedCreatorsRequest(): RequestDescriptor

    fun parseRecommendedCreators(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<List<FanboxCreatorDetail>>>

    fun buildCreatorPlansRequest(creatorId: String): RequestDescriptor

    fun parseCreatorPlans(body: String, statusCode: Int): GuestParseResult<FanboxTolerantResult<List<FanboxCreatorPlan>>>

    fun buildCreatorPlanDetailRequest(creatorId: String): RequestDescriptor

    fun parseCreatorPlanDetail(body: String, statusCode: Int): GuestParseResult<FanboxCreatorPlanDetail>

    fun buildCreatorTagsRequest(creatorId: String): RequestDescriptor

    fun parseCreatorTags(body: String, statusCode: Int): GuestParseResult<List<FanboxTag>>

    fun buildFollowCreatorRequest(userId: Long): RequestDescriptor

    fun buildUnfollowCreatorRequest(userId: Long): RequestDescriptor

    fun buildSearchCreatorsRequest(query: String, page: Int): RequestDescriptor

    fun parseSearchCreators(body: String, statusCode: Int): GuestParseResult<PageNumberInfo<FanboxCreatorDetail>>

    fun buildSearchTagsRequest(query: String): RequestDescriptor

    fun parseSearchTags(body: String, statusCode: Int): GuestParseResult<List<FanboxTag>>
}

/**
 * The outcome of a guest-side response interpretation.
 *
 * Failures travel as a value because an exception crossing the Zipline bridge arrives as an opaque
 * type.
 */
@Serializable
internal sealed interface GuestParseResult<out T> {
    /** The response matched the schema the guest expects. */
    @Serializable
    data class Success<out T>(val value: T) : GuestParseResult<T>

    /** The guest's parser rejected the response. */
    @Serializable
    data class SchemaMismatch(val diagnostic: String) : GuestParseResult<Nothing>

    /** The guest failed for a reason other than the response. */
    @Serializable
    data class GuestFailure(val diagnostic: String) : GuestParseResult<Nothing>
}
