package me.matsumo.fankt.fanbox.repository

import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.matsumo.fankt.fanbox.datasource.FanboxPostApi
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId

internal class FanboxPostRepository(
    private val fanboxPostApi: FanboxPostApi,
    private val fanboxPostApiWithoutContentNegotiation: FanboxPostApi,
    private val fanboxPostMapper: FanboxPostMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun getHomePosts(cursor: FanboxCursor?) = withContext(ioDispatcher) {
        fanboxPostApi.getHomePosts(
            loadSize = cursor?.limit?.toString() ?: LOAD_SIZE,
            maxPublishedDatetime = cursor?.maxPublishedDatetime,
            maxId = cursor?.maxId,
        ).let {
            fanboxPostMapper.map(it)
        }
    }

    suspend fun getSupportedPosts(cursor: FanboxCursor?) = withContext(ioDispatcher) {
        fanboxPostApi.getSupportedPosts(
            loadSize = cursor?.limit?.toString() ?: LOAD_SIZE,
            maxPublishedDatetime = cursor?.maxPublishedDatetime,
            maxId = cursor?.maxId,
        ).let {
            fanboxPostMapper.map(it)
        }
    }

    suspend fun getCreatorPosts(creatorId: FanboxCreatorId, cursor: FanboxCursor?, nextCursor: FanboxCursor?) = withContext(ioDispatcher) {
        val cursors = if (cursor == null) {
            val pagination = getCreatorPostsPagination(creatorId)
            pagination.first() to pagination.elementAtOrNull(1)
        } else {
            cursor to nextCursor
        }

        fanboxPostApi.getCreatorPosts(
            creatorId = creatorId.value,
            loadSize = cursor?.limit?.toString() ?: LOAD_SIZE,
            maxPublishedDatetime = cursors.first.maxPublishedDatetime,
            maxId = cursors.first.maxId,
        ).let {
            fanboxPostMapper.map(it, cursors.second)
        }
    }

    suspend fun getCreatorPostsPagination(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fanboxPostApi.getCreatorPostsPagination(creatorId.toString()).let {
            fanboxPostMapper.map(it)
        }
    }

    suspend fun getPostDetail(postId: FanboxPostId) = withContext(ioDispatcher) {
        fanboxPostApi.getPostDetail(postId.value).let {
            fanboxPostMapper.map(it)
        }
    }

    suspend fun getPostComment(postId: FanboxPostId, offset: Int) = withContext(ioDispatcher) {
        fanboxPostApi.getPostComment(
            postId = postId.value,
            offset = offset,
            loadSize = LOAD_SIZE,
        ).let {
            fanboxPostMapper.map(it)
        }
    }

    suspend fun getPostFromQuery(query: String, creatorId: FanboxCreatorId?, page: Int) = withContext(ioDispatcher) {
        fanboxPostApi.getPostFromQuery(
            query = query,
            creatorId = creatorId?.value,
            page = page,
        ).let {
            fanboxPostMapper.map(it)
        }
    }

    suspend fun likePost(postId: FanboxPostId) = withContext(ioDispatcher) {
        fanboxPostApiWithoutContentNegotiation.likePost(
            TextContent(
                text = buildJsonObject { put("postId", postId.toString()) }.toString(),
                contentType = ContentType.Application.Json,
            ),
        )
    }

    suspend fun likeComment(commentId: FanboxCommentId) = withContext(ioDispatcher) {
        fanboxPostApiWithoutContentNegotiation.likeComment(
            TextContent(
                text = buildJsonObject { put("commentId", commentId.toString()) }.toString(),
                contentType = ContentType.Application.Json,
            ),
        )
    }

    suspend fun addComment(
        postId: FanboxPostId,
        rootCommentId: FanboxCommentId,
        parentCommentId: FanboxCommentId,
        comment: String,
    ) = withContext(ioDispatcher) {
        fanboxPostApiWithoutContentNegotiation.addComment(
            TextContent(
                text = buildJsonObject {
                    put("postId", postId.toString())
                    put("rootCommentId", rootCommentId.toString())
                    put("parentCommentId", parentCommentId.toString())
                    put("body", comment)
                }.toString(),
                contentType = ContentType.Application.Json,
            ),
        )
    }

    suspend fun deleteComment(commentId: FanboxCommentId) = withContext(ioDispatcher) {
        fanboxPostApiWithoutContentNegotiation.deleteComment(
            TextContent(
                text = buildJsonObject { put("commentId", commentId.toString()) }.toString(),
                contentType = ContentType.Application.Json,
            ),
        )
    }

    companion object {
        private const val LOAD_SIZE = "20"
    }
}
