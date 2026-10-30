package me.matsumo.fankt.fanbox.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.FanboxTolerantResult
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.PageCursorInfo
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointIds
import me.matsumo.fankt.fanbox.guest.FanboxGuestHost
import me.matsumo.fankt.fanbox.reportedTo
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink

/** 投稿に関する endpoint を guest で実行する。 */
internal class FanboxPostRepository(
    private val guestHost: FanboxGuestHost,
    private val diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun getHomePosts(cursor: FanboxCursor?) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.postListHome,
            build = { buildHomePostsRequest(cursor) },
            parse = { response -> parseHomePosts(response.bodyText, response.statusCode) },
        ).reportedTo(diagnosticSink)
    }

    suspend fun getSupportedPosts(cursor: FanboxCursor?) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.postListSupporting,
            build = { buildSupportingPostsRequest(cursor) },
            parse = { response -> parseSupportingPosts(response.bodyText, response.statusCode) },
        ).reportedTo(diagnosticSink)
    }

    suspend fun getCreatorPosts(
        creatorId: FanboxCreatorId,
        cursor: FanboxCursor?,
        nextCursor: FanboxCursor?,
    ) = withContext(ioDispatcher) {
        val (currentCursor, resolvedNextCursor) = if (cursor == null) {
            val pagination = fetchCreatorPostsPagination(creatorId)
            val firstCursor = pagination.firstOrNull()
                ?: return@withContext FanboxTolerantResult(
                    value = PageCursorInfo(contents = emptyList(), cursor = null),
                    mismatches = emptyList(),
                )
            firstCursor to pagination.elementAtOrNull(1)
        } else {
            cursor to nextCursor
        }

        guestHost.request(
            endpointId = FanboxEndpointIds.postListCreator,
            build = { buildCreatorPostsRequest(creatorId.value, currentCursor) },
            parse = { response ->
                parseCreatorPosts(
                    body = response.bodyText,
                    statusCode = response.statusCode,
                    nextCursor = resolvedNextCursor,
                )
            },
        ).reportedTo(diagnosticSink)
    }

    suspend fun getCreatorPostsPagination(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fetchCreatorPostsPagination(creatorId)
    }

    suspend fun getPostDetail(postId: FanboxPostId) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.postInfo,
            build = { buildPostDetailRequest(postId.value) },
            parse = { response -> parsePostDetail(response.bodyText, response.statusCode) },
        )
    }

    suspend fun getPostComment(postId: FanboxPostId, offset: Int) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.postGetComments,
            build = { buildPostCommentsRequest(postId.value, offset) },
            parse = { response -> parsePostComments(response.bodyText, response.statusCode) },
        ).reportedTo(diagnosticSink)
    }

    suspend fun getPostFromQuery(
        query: String,
        creatorId: FanboxCreatorId?,
        page: Int,
    ) = withContext(ioDispatcher) {
        guestHost.request(
            endpointId = FanboxEndpointIds.postListTagged,
            build = {
                buildTaggedPostsRequest(
                    tag = query,
                    creatorId = creatorId?.value,
                    page = page,
                )
            },
            parse = { response -> parseTaggedPosts(response.bodyText, response.statusCode) },
        )
    }

    suspend fun likePost(postId: FanboxPostId) = withContext(ioDispatcher) {
        guestHost.send(FanboxEndpointIds.postLikePost) { buildLikePostRequest(postId.value) }
    }

    suspend fun likeComment(commentId: FanboxCommentId) = withContext(ioDispatcher) {
        guestHost.send(FanboxEndpointIds.postLikeComment) { buildLikeCommentRequest(commentId.value) }
    }

    suspend fun addComment(
        postId: FanboxPostId,
        rootCommentId: FanboxCommentId?,
        parentCommentId: FanboxCommentId?,
        comment: String,
    ) = withContext(ioDispatcher) {
        guestHost.send(FanboxEndpointIds.postAddComment) {
            buildAddCommentRequest(
                postId = postId.value,
                rootCommentId = rootCommentId?.value,
                parentCommentId = parentCommentId?.value,
                comment = comment,
            )
        }
    }

    suspend fun deleteComment(commentId: FanboxCommentId) = withContext(ioDispatcher) {
        guestHost.send(FanboxEndpointIds.postDeleteComment) { buildDeleteCommentRequest(commentId.value) }
    }

    private suspend fun fetchCreatorPostsPagination(creatorId: FanboxCreatorId) = guestHost.request(
        endpointId = FanboxEndpointIds.postPaginateCreator,
        build = { buildCreatorPostsPaginationRequest(creatorId.value) },
        parse = { response -> parseCreatorPostsPagination(response.bodyText, response.statusCode) },
    )
}
