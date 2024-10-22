package me.matsumo.fankt.repository

import me.matsumo.fankt.common.toInt
import me.matsumo.fankt.common.toJsonObject
import me.matsumo.fankt.datasource.FanboxCreatorApi
import me.matsumo.fankt.datasource.FanboxPostApi
import me.matsumo.fankt.datasource.FanboxSearchApi
import me.matsumo.fankt.datasource.FanboxUserApi
import me.matsumo.fankt.datasource.mapper.translate
import me.matsumo.fankt.domain.FanboxCursor
import me.matsumo.fankt.domain.PageCursorInfo
import me.matsumo.fankt.domain.PageNumberInfo
import me.matsumo.fankt.domain.PageOffsetInfo
import me.matsumo.fankt.domain.model.FanboxBell
import me.matsumo.fankt.domain.model.FanboxComment
import me.matsumo.fankt.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.domain.model.FanboxCreatorPlanDetail
import me.matsumo.fankt.domain.model.FanboxCreatorTag
import me.matsumo.fankt.domain.model.FanboxNewsLetter
import me.matsumo.fankt.domain.model.FanboxPaidRecord
import me.matsumo.fankt.domain.model.FanboxPost
import me.matsumo.fankt.domain.model.FanboxPostDetail
import me.matsumo.fankt.domain.model.FanboxTag
import me.matsumo.fankt.domain.model.id.FanboxCommentId
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxPostId

internal class FanboxRepository(
    private val postApi: FanboxPostApi,
    private val creatorApi: FanboxCreatorApi,
    private val userApi: FanboxUserApi,
    private val searchApi: FanboxSearchApi,
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
        return postApi.getCreatorPosts(
            creatorId = creatorId.value,
            loadSize = loadSize.toString(),
            maxPublishedDatetime = currentCursor.maxPublishedDatetime,
            maxId = currentCursor.maxId,
        ).translate(nextCursor)
    }

    suspend fun getPostDetail(postId: FanboxPostId): FanboxPostDetail {
        return postApi.getPostDetail(postId.value).translate()
    }

    suspend fun getPostComments(
        postId: FanboxPostId,
        offset: Int = 0,
    ): PageOffsetInfo<FanboxComment> {
        return postApi.getPostComment(
            postId = postId.value,
            offset = offset,
            loadSize = DEFAULT_LOAD_SIZE.toString(),
        ).translate()
    }

    suspend fun getPostFromQuery(
        query: String,
        creatorId: FanboxCreatorId? = null,
        page: Int = 0,
    ): PageNumberInfo<FanboxPost> {
        return postApi.getPostFromQuery(
            query = query,
            creatorId = creatorId?.value,
            page = page,
        ).translate()
    }

    suspend fun likePost(postId: FanboxPostId) {
        val body = mapOf("postId" to postId.value).toJsonObject()
        return postApi.likePost(body)
    }

    suspend fun likeComment(commentId: FanboxCommentId) {
        val body = mapOf("commentId" to commentId.value).toJsonObject()
        return postApi.likeComment(body)
    }

    suspend fun addComment(
        postId: FanboxPostId,
        comment: String,
        rootCommentId: FanboxCommentId? = null,
        parentCommentId: FanboxCommentId? = null,
    ) {
        val body = mapOf(
            "postId" to postId.value,
            "rootCommentId" to rootCommentId?.value.orEmpty(),
            "parentCommentId" to parentCommentId?.value.orEmpty(),
            "body" to comment,
        ).toJsonObject()
        return postApi.addComment(body)
    }

    suspend fun deleteComment(commentId: FanboxCommentId) {
        val body = mapOf("commentId" to commentId.value).toJsonObject()
        return postApi.deleteComment(body)
    }

    suspend fun getCreatorDetail(creatorId: FanboxCreatorId): FanboxCreatorDetail {
        return creatorApi.getCreatorDetail(creatorId.value).translate()
    }

    suspend fun getFollowingCreators(): List<FanboxCreatorDetail> {
        return creatorApi.getFollowingCreators().translate()
    }

    suspend fun getFollowingPixivCreators(): List<FanboxCreatorDetail> {
        return creatorApi.getFollowingPixivCreators().translate()
    }

    suspend fun getRecommendedCreators(
        loadSize: Int = DEFAULT_LOAD_SIZE,
    ): List<FanboxCreatorDetail> {
        return creatorApi.getRecommendedCreators(loadSize.toString()).translate()
    }

    suspend fun getCreatorPlans(creatorId: FanboxCreatorId): List<FanboxCreatorPlan> {
        return creatorApi.getCreatorPlans(creatorId.value).translate()
    }

    suspend fun getCreatorPlanDetail(creatorId: FanboxCreatorId): FanboxCreatorPlanDetail {
        return creatorApi.getCreatorPlanDetail(creatorId.value).translate()
    }

    suspend fun getCreatorTags(creatorId: FanboxCreatorId): List<FanboxCreatorTag> {
        return creatorApi.getCreatorTags(creatorId.value).translate()
    }

    suspend fun followCreator(creatorId: FanboxCreatorId) {
        val body = mapOf("creatorId" to creatorId.value).toJsonObject()
        return creatorApi.followCreator(body)
    }

    suspend fun unfollowCreator(creatorId: FanboxCreatorId) {
        val body = mapOf("creatorId" to creatorId.value).toJsonObject()
        return creatorApi.unfollowCreator(body)
    }

    suspend fun getSupportedPlans(): List<FanboxCreatorPlan> {
        return userApi.getSupportedPlans().translate()
    }

    suspend fun getPaidRecords(): List<FanboxPaidRecord> {
        return userApi.getPaidRecords().translate()
    }

    suspend fun getUnpaidRecords(): List<FanboxPaidRecord> {
        return userApi.getUnpaidRecords().translate()
    }

    suspend fun getNewsLetters(): List<FanboxNewsLetter> {
        return userApi.getNewsLetters().translate()
    }

    suspend fun getBells(
        page: Int = 0,
        skipConvertUnreadNotification: Boolean = false,
        commentOnly: Boolean = false,
    ): PageNumberInfo<FanboxBell> {
        return userApi.getBells(
            page = page,
            skipConvertUnreadNotification = skipConvertUnreadNotification.toInt(),
            commentOnly = commentOnly.toInt(),
        ).translate()
    }

    suspend fun getCreatorFromQuery(
        query: String,
        page: Int = 0,
    ): PageNumberInfo<FanboxCreatorDetail> {
        return searchApi.getCreatorFromQuery(
            query = query,
            page = page,
        ).translate()
    }

    suspend fun getTagFromQuery(query: String): List<FanboxTag> {
        return searchApi.getTagFromQuery(query).translate()
    }

    companion object {
        private const val DEFAULT_LOAD_SIZE = 10
    }
}
