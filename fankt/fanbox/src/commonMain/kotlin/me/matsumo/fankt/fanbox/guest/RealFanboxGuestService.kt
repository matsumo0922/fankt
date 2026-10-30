package me.matsumo.fankt.fanbox.guest

import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.FanboxTolerantResult
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.PageCursorInfo
import me.matsumo.fankt.fanbox.domain.PageNumberInfo
import me.matsumo.fankt.fanbox.domain.PageOffsetInfo
import me.matsumo.fankt.fanbox.domain.model.FanboxComment
import me.matsumo.fankt.fanbox.domain.model.FanboxPost
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
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
