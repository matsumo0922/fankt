package me.matsumo.fankt.datasource.impl

import me.matsumo.fankt.common.toInt
import me.matsumo.fankt.common.toJsonObject
import me.matsumo.fankt.datasource.FanboxApi
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

internal class FanboxApiImpl(
    private val postApi: FanboxPostApi,
    private val creatorApi: FanboxCreatorApi,
    private val userApi: FanboxUserApi,
    private val searchApi: FanboxSearchApi,
): FanboxApi {

    override suspend fun getHomePosts(
        cursor: FanboxCursor?,
        loadSize: Int,
    ): PageCursorInfo<FanboxPost> {
        return postApi.getHomePosts(
            loadSize = loadSize.toString(),
            maxPublishedDatetime = cursor?.maxPublishedDatetime,
            maxId = cursor?.maxId,
        ).translate()
    }

    override suspend fun getSupportedPosts(
        cursor: FanboxCursor,
        loadSize: Int,
    ): PageCursorInfo<FanboxPost> {
        return postApi.getSupportedPosts(
            loadSize = loadSize.toString(),
            maxPublishedDatetime = cursor.maxPublishedDatetime,
            maxId = cursor.maxId,
        ).translate()
    }

    override suspend fun getCreatorPosts(
        creatorId: FanboxCreatorId,
        currentCursor: FanboxCursor,
        nextCursor: FanboxCursor?,
        loadSize: Int,
    ): PageCursorInfo<FanboxPost> {
        return postApi.getCreatorPosts(
            creatorId = creatorId.value,
            loadSize = loadSize.toString(),
            maxPublishedDatetime = currentCursor.maxPublishedDatetime,
            maxId = currentCursor.maxId,
        ).translate(nextCursor)
    }

    override suspend fun getPostDetail(postId: FanboxPostId): FanboxPostDetail {
        return postApi.getPostDetail(postId.value).translate()
    }

    override suspend fun getPostComments(
        postId: FanboxPostId,
        offset: Int,
    ): PageOffsetInfo<FanboxComment> {
        return postApi.getPostComment(
            postId = postId.value,
            offset = offset,
            loadSize = FanboxApi.DEFAULT_LOAD_SIZE.toString(),
        ).translate()
    }

    override suspend fun getPostFromQuery(
        query: String,
        creatorId: FanboxCreatorId?,
        page: Int,
    ): PageNumberInfo<FanboxPost> {
        return postApi.getPostFromQuery(
            query = query,
            creatorId = creatorId?.value,
            page = page,
        ).translate()
    }

    override suspend fun likePost(postId: FanboxPostId) {
        val body = mapOf("postId" to postId.value).toJsonObject()
        return postApi.likePost(body)
    }

    override suspend fun likeComment(commentId: FanboxCommentId) {
        val body = mapOf("commentId" to commentId.value).toJsonObject()
        return postApi.likeComment(body)
    }

    override suspend fun addComment(
        postId: FanboxPostId,
        comment: String,
        rootCommentId: FanboxCommentId?,
        parentCommentId: FanboxCommentId?,
    ) {
        val body = mapOf(
            "postId" to postId.value,
            "rootCommentId" to rootCommentId?.value.orEmpty(),
            "parentCommentId" to parentCommentId?.value.orEmpty(),
            "body" to comment,
        ).toJsonObject()
        return postApi.addComment(body)
    }

    override suspend fun deleteComment(commentId: FanboxCommentId) {
        val body = mapOf("commentId" to commentId.value).toJsonObject()
        return postApi.deleteComment(body)
    }

    override suspend fun getCreatorDetail(creatorId: FanboxCreatorId): FanboxCreatorDetail {
        return creatorApi.getCreatorDetail(creatorId.value).translate()
    }

    override suspend fun getFollowingCreators(): List<FanboxCreatorDetail> {
        return creatorApi.getFollowingCreators().translate()
    }

    override suspend fun getFollowingPixivCreators(): List<FanboxCreatorDetail> {
        return creatorApi.getFollowingPixivCreators().translate()
    }

    override suspend fun getRecommendedCreators(loadSize: Int): List<FanboxCreatorDetail> {
        return creatorApi.getRecommendedCreators(loadSize.toString()).translate()
    }

    override suspend fun getCreatorPlans(creatorId: FanboxCreatorId): List<FanboxCreatorPlan> {
        return creatorApi.getCreatorPlans(creatorId.value).translate()
    }

    override suspend fun getCreatorPlanDetail(creatorId: FanboxCreatorId): FanboxCreatorPlanDetail {
        return creatorApi.getCreatorPlanDetail(creatorId.value).translate()
    }

    override suspend fun getCreatorTags(creatorId: FanboxCreatorId): List<FanboxCreatorTag> {
        return creatorApi.getCreatorTags(creatorId.value).translate()
    }

    override suspend fun followCreator(creatorId: FanboxCreatorId) {
        val body = mapOf("creatorId" to creatorId.value).toJsonObject()
        return creatorApi.followCreator(body)
    }

    override suspend fun unfollowCreator(creatorId: FanboxCreatorId) {
        val body = mapOf("creatorId" to creatorId.value).toJsonObject()
        return creatorApi.unfollowCreator(body)
    }

    override suspend fun getSupportedPlans(): List<FanboxCreatorPlan> {
        return userApi.getSupportedPlans().translate()
    }

    override suspend fun getPaidRecords(): List<FanboxPaidRecord> {
        return userApi.getPaidRecords().translate()
    }

    override suspend fun getUnpaidRecords(): List<FanboxPaidRecord> {
        return userApi.getUnpaidRecords().translate()
    }

    override suspend fun getNewsLetters(): List<FanboxNewsLetter> {
        return userApi.getNewsLetters().translate()
    }

    override suspend fun getBells(
        page: Int,
        skipConvertUnreadNotification: Boolean,
        commentOnly: Boolean,
    ): PageNumberInfo<FanboxBell> {
        return userApi.getBells(
            page = page,
            skipConvertUnreadNotification = skipConvertUnreadNotification.toInt(),
            commentOnly = commentOnly.toInt(),
        ).translate()
    }

    override suspend fun getCreatorFromQuery(
        query: String,
        page: Int,
    ): PageNumberInfo<FanboxCreatorDetail> {
        return searchApi.getCreatorFromQuery(
            query = query,
            page = page,
        ).translate()
    }

    override suspend fun getTagFromQuery(query: String): List<FanboxTag> {
        return searchApi.getTagFromQuery(query).translate()
    }
}
