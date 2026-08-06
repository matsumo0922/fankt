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
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.guest.FanboxGuestHost
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.response.FanboxResponses
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor

internal class FanboxPostRepository(
    private val requestExecutor: FanboxRequestExecutor,
    private val diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    private val includeRawFragment: Boolean = false,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val guestHost: FanboxGuestHost? = null,
) {

    suspend fun getHomePosts(cursor: FanboxCursor?) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.homePosts(cursor))
        FanboxResponses.homePosts(
            body = response.bodyText,
            statusCode = response.statusCode,
            diagnosticSink = diagnosticSink,
            includeRawFragment = includeRawFragment,
        )
    }

    suspend fun getSupportedPosts(cursor: FanboxCursor?) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.supportingPosts(cursor))
        FanboxResponses.supportingPosts(
            body = response.bodyText,
            statusCode = response.statusCode,
            diagnosticSink = diagnosticSink,
            includeRawFragment = includeRawFragment,
        )
    }

    suspend fun getCreatorPosts(creatorId: FanboxCreatorId, cursor: FanboxCursor?, nextCursor: FanboxCursor?) = withContext(ioDispatcher) {
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

        val response = requestExecutor.execute(FanboxEndpoints.creatorPosts(creatorId, currentCursor))
        FanboxResponses.creatorPosts(
            body = response.bodyText,
            nextCursor = resolvedNextCursor,
            statusCode = response.statusCode,
            diagnosticSink = diagnosticSink,
            includeRawFragment = includeRawFragment,
        )
    }

    suspend fun getCreatorPostsPagination(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fetchCreatorPostsPagination(creatorId)
    }

    suspend fun getPostDetail(postId: FanboxPostId) = withContext(ioDispatcher) {
        suspend fun direct() = requestExecutor.execute(FanboxEndpoints.postDetail(postId)).let { response ->
            FanboxResponses.postDetail(response.bodyText, response.statusCode)
        }

        guestHost?.getPostDetail(postId, requestExecutor, ::direct) ?: direct()
    }

    suspend fun getPostComment(postId: FanboxPostId, offset: Int) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.postComments(postId, offset))
        FanboxResponses.postComments(
            body = response.bodyText,
            statusCode = response.statusCode,
            diagnosticSink = diagnosticSink,
            includeRawFragment = includeRawFragment,
        )
    }

    suspend fun getPostFromQuery(query: String, creatorId: FanboxCreatorId?, page: Int) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.taggedPosts(query, creatorId, page))
        FanboxResponses.taggedPosts(response.bodyText, response.statusCode)
    }

    suspend fun likePost(postId: FanboxPostId) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.likePost(postId))
        FanboxResponses.likePost(response.bodyText)
    }

    suspend fun likeComment(commentId: FanboxCommentId) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.likeComment(commentId))
        FanboxResponses.likeComment(response.bodyText)
    }

    suspend fun addComment(
        postId: FanboxPostId,
        rootCommentId: FanboxCommentId?,
        parentCommentId: FanboxCommentId?,
        comment: String,
    ) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(
            FanboxEndpoints.addComment(postId, rootCommentId, parentCommentId, comment),
        )
        FanboxResponses.addComment(response.bodyText)
    }

    suspend fun deleteComment(commentId: FanboxCommentId) = withContext(ioDispatcher) {
        val response = requestExecutor.execute(FanboxEndpoints.deleteComment(commentId))
        FanboxResponses.deleteComment(response.bodyText)
    }

    private suspend fun fetchCreatorPostsPagination(creatorId: FanboxCreatorId): List<FanboxCursor> {
        val response = requestExecutor.execute(FanboxEndpoints.creatorPostsPagination(creatorId))
        return FanboxResponses.creatorPostsPagination(response.bodyText, response.statusCode)
    }
}
