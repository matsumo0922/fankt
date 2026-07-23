package me.matsumo.fankt.fanbox.repository

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.matsumo.fankt.fanbox.FanboxExceptionFactory
import me.matsumo.fankt.fanbox.FanboxTolerantResult
import me.matsumo.fankt.fanbox.datasource.FanboxPostApi
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.PageCursorInfo
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
            firstPublishedDatetime = cursor?.firstPublishedDatetime,
            maxPublishedDatetime = cursor?.maxPublishedDatetime,
            firstId = cursor?.firstId,
            maxId = cursor?.maxId,
        ).let {
            fanboxPostMapper.map(it, "post.listHome")
        }
    }

    suspend fun getSupportedPosts(cursor: FanboxCursor?) = withContext(ioDispatcher) {
        fanboxPostApi.getSupportedPosts(
            loadSize = cursor?.limit?.toString() ?: LOAD_SIZE,
            firstPublishedDatetime = cursor?.firstPublishedDatetime,
            maxPublishedDatetime = cursor?.maxPublishedDatetime,
            firstId = cursor?.firstId,
            maxId = cursor?.maxId,
        ).let {
            fanboxPostMapper.map(it, "post.listSupporting")
        }
    }

    suspend fun getCreatorPosts(creatorId: FanboxCreatorId, cursor: FanboxCursor?, nextCursor: FanboxCursor?) = withContext(ioDispatcher) {
        val (currentCursor, resolvedNextCursor) = if (cursor == null) {
            val pagination = getCreatorPostsPagination(creatorId)
            val firstCursor = pagination.firstOrNull()
                ?: return@withContext FanboxTolerantResult(
                    value = PageCursorInfo(contents = emptyList(), cursor = null),
                    mismatches = emptyList(),
                )
            firstCursor to pagination.elementAtOrNull(1)
        } else {
            cursor to nextCursor
        }

        fanboxPostApi.getCreatorPosts(
            creatorId = creatorId.value,
            loadSize = currentCursor.limit?.toString() ?: LOAD_SIZE,
            firstPublishedDatetime = currentCursor.firstPublishedDatetime,
            maxPublishedDatetime = currentCursor.maxPublishedDatetime,
            firstId = currentCursor.firstId,
            maxId = currentCursor.maxId,
        ).let {
            fanboxPostMapper.map(it, resolvedNextCursor, "post.listCreator")
        }
    }

    suspend fun getCreatorPostsPagination(creatorId: FanboxCreatorId) = withContext(ioDispatcher) {
        fanboxPostApi.getCreatorPostsPagination(creatorId.toString()).let {
            fanboxPostMapper.map(it)
        }
    }

    suspend fun getPostDetail(postId: FanboxPostId) = withContext(ioDispatcher) {
        val entity = fanboxPostApi.getPostDetail(postId.value)
        try {
            fanboxPostMapper.map(entity)
        } catch (failure: SerializationException) {
            throw FanboxExceptionFactory.schemaMismatch(
                statusCode = HttpStatusCode.OK.value,
                html = entity.body.post.body?.toString().orEmpty(),
                endpoint = "post.info",
                cause = failure,
            )
        }
    }

    suspend fun getPostComment(postId: FanboxPostId, offset: Int) = withContext(ioDispatcher) {
        fanboxPostApi.getPostComment(
            postId = postId.value,
            offset = offset,
            loadSize = LOAD_SIZE,
        ).let {
            fanboxPostMapper.map(it, "post.getComments")
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
        rootCommentId: FanboxCommentId?,
        parentCommentId: FanboxCommentId?,
        comment: String,
    ) = withContext(ioDispatcher) {
        fanboxPostApiWithoutContentNegotiation.addComment(
            TextContent(
                text = buildJsonObject {
                    put("postId", postId.toString())
                    rootCommentId?.let { put("rootCommentId", it.toString()) }
                    parentCommentId?.let { put("parentCommentId", it.toString()) }
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
