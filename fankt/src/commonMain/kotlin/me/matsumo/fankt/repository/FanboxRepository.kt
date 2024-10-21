package me.matsumo.fankt.repository

import me.matsumo.fankt.datasource.FanboxPostApi
import me.matsumo.fankt.datasource.mapper.translate
import me.matsumo.fankt.domain.FanboxCursor
import me.matsumo.fankt.domain.PageCursorInfo
import me.matsumo.fankt.domain.PageNumberInfo
import me.matsumo.fankt.domain.PageOffsetInfo
import me.matsumo.fankt.domain.model.FanboxComment
import me.matsumo.fankt.domain.model.FanboxPost
import me.matsumo.fankt.domain.model.FanboxPostDetail
import me.matsumo.fankt.domain.model.id.FanboxCommentId
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxPostId

internal class FanboxRepository(
    private val postApi: FanboxPostApi,
) {

    suspend fun getHomePosts(
        cursor: FanboxCursor?,
        loadSize: Int = cursor?.limit ?: DEFAULT_LOAD_SIZE,
    ): PageCursorInfo<FanboxPost> {
        return postApi.getHomePosts(
            loadSize = loadSize.toString(),
            maxPublishedDatetime = cursor?.maxPublishedDatetime,
            maxId = cursor?.maxId,
        ).translate()
    }

    suspend fun getSupportedPosts(
        cursor: FanboxCursor,
        loadSize: Int = cursor.limit ?: DEFAULT_LOAD_SIZE,
    ): PageCursorInfo<FanboxPost> {
        return postApi.getSupportedPosts(
            loadSize = loadSize.toString(),
            maxPublishedDatetime = cursor.maxPublishedDatetime,
            maxId = cursor.maxId,
        ).translate()
    }

    suspend fun getCreatorPosts(
        creatorId: FanboxCreatorId,
        currentCursor: FanboxCursor,
        nextCursor: FanboxCursor?,
        loadSize: Int = currentCursor.limit ?: DEFAULT_LOAD_SIZE,
    ): PageCursorInfo<FanboxPost> {
        postApi.getCreatorPosts(
            creatorId = creatorId.value,
            loadSize = loadSize.toString(),
            maxPublishedDatetime = currentCursor.maxPublishedDatetime,
            maxId = currentCursor.maxId,
        ).translate()
    }

    suspend fun getPostDetail(postId: FanboxPostId): FanboxPostDetail {
        TODO()
    }

    suspend fun getPostComments(
        postId: FanboxPostId,
        offset: Int = 0,
    ): PageOffsetInfo<FanboxComment> {
        TODO()
    }

    suspend fun getPostFromQuery(
        query: String,
        creatorId: FanboxCreatorId? = null,
        page: Int = 0,
    ): PageNumberInfo<FanboxPost> {
        TODO()
    }

    suspend fun likePost(postId: FanboxPostId) {
        TODO()
    }

    suspend fun likeComment(commentId: FanboxCommentId) {
        TODO()
    }

    suspend fun addComment(
        postId: FanboxPostId,
        content: String,
        rootCommentId: FanboxCommentId? = null,
        parentCommentId: FanboxCommentId? = null,
    ) {
        TODO()
    }

    suspend fun deleteComment(commentId: FanboxCommentId) {
        TODO()
    }

    companion object {
        private const val DEFAULT_LOAD_SIZE = 10
    }
}
