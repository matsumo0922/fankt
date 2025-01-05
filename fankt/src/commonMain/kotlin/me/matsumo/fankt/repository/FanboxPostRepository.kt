package me.matsumo.fankt.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.matsumo.fankt.datasource.FanboxPostApi
import me.matsumo.fankt.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.domain.FanboxCursor
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxPostId

internal class FanboxPostRepository(
    private val fanboxPostApi: FanboxPostApi,
    private val fanboxPostApiWithoutContentNegotiation: FanboxPostApi,
    private val fanboxPostMapper: FanboxPostMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun getHomePosts(cursor: FanboxCursor?) = withContext(ioDispatcher) {
        fanboxPostApi.getHomePosts(
            loadSize = LOAD_SIZE,
            maxPublishedDatetime = cursor?.maxPublishedDatetime,
            maxId = cursor?.maxId,
        ).let {
            fanboxPostMapper.map(it)
        }
    }

    suspend fun getSupportedPosts(cursor: FanboxCursor?) = withContext(ioDispatcher) {
        fanboxPostApi.getSupportedPosts(
            loadSize = LOAD_SIZE,
            maxPublishedDatetime = cursor?.maxPublishedDatetime,
            maxId = cursor?.maxId,
        ).let {
            fanboxPostMapper.map(it)
        }
    }

    suspend fun getCreatorPosts(creatorId: FanboxCreatorId, cursor: FanboxCursor?) = withContext(ioDispatcher) {
        fanboxPostApi.getCreatorPosts(
            creatorId = creatorId.value,
            loadSize = LOAD_SIZE,
            maxPublishedDatetime = cursor?.maxPublishedDatetime,
            maxId = cursor?.maxId,
        ).let {
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
            body = JsonObject(mapOf("postId" to JsonPrimitive(postId.value)))
        )
    }

    suspend fun addComment(postId: FanboxPostId, comment: String) = withContext(ioDispatcher) {
        fanboxPostApiWithoutContentNegotiation.addComment(
            body = JsonObject(
                mapOf(
                    "postId" to JsonPrimitive(postId.value),
                    "comment" to JsonPrimitive(comment),
                ),
            ),
        )
    }

    suspend fun deleteComment(postId: FanboxPostId, commentId: String) = withContext(ioDispatcher) {
        fanboxPostApiWithoutContentNegotiation.deleteComment(
            body = JsonObject(
                mapOf(
                    "postId" to JsonPrimitive(postId.value),
                    "commentId" to JsonPrimitive(commentId),
                ),
            ),
        )
    }

    companion object {
        private const val LOAD_SIZE = "20"
    }
}