package me.matsumo.fankt.datasource

import me.matsumo.fankt.common.toInt
import me.matsumo.fankt.common.toJsonObject
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

interface FanboxApi {
    suspend fun getHomePosts(
        cursor: FanboxCursor?,
        loadSize: Int = cursor?.limit ?: DEFAULT_LOAD_SIZE,
    ): PageCursorInfo<FanboxPost>

    suspend fun getSupportedPosts(
        cursor: FanboxCursor,
        loadSize: Int = cursor.limit ?: DEFAULT_LOAD_SIZE,
    ): PageCursorInfo<FanboxPost>

    suspend fun getCreatorPosts(
        creatorId: FanboxCreatorId,
        currentCursor: FanboxCursor,
        nextCursor: FanboxCursor?,
        loadSize: Int = currentCursor.limit ?: DEFAULT_LOAD_SIZE,
    ): PageCursorInfo<FanboxPost>

    suspend fun getPostDetail(postId: FanboxPostId): FanboxPostDetail

    suspend fun getPostComments(
        postId: FanboxPostId,
        offset: Int = 0,
    ): PageOffsetInfo<FanboxComment>

    suspend fun getPostFromQuery(
        query: String,
        creatorId: FanboxCreatorId? = null,
        page: Int = 0,
    ): PageNumberInfo<FanboxPost>

    suspend fun likePost(postId: FanboxPostId)

    suspend fun likeComment(commentId: FanboxCommentId)

    suspend fun addComment(
        postId: FanboxPostId,
        comment: String,
        rootCommentId: FanboxCommentId? = null,
        parentCommentId: FanboxCommentId? = null,
    )

    suspend fun deleteComment(commentId: FanboxCommentId)

    suspend fun getCreatorDetail(creatorId: FanboxCreatorId): FanboxCreatorDetail

    suspend fun getFollowingCreators(): List<FanboxCreatorDetail>

    suspend fun getFollowingPixivCreators(): List<FanboxCreatorDetail>

    suspend fun getRecommendedCreators(
        loadSize: Int = DEFAULT_LOAD_SIZE,
    ): List<FanboxCreatorDetail>

    suspend fun getCreatorPlans(creatorId: FanboxCreatorId): List<FanboxCreatorPlan>

    suspend fun getCreatorPlanDetail(creatorId: FanboxCreatorId): FanboxCreatorPlanDetail

    suspend fun getCreatorTags(creatorId: FanboxCreatorId): List<FanboxCreatorTag>

    suspend fun followCreator(creatorId: FanboxCreatorId)

    suspend fun unfollowCreator(creatorId: FanboxCreatorId)

    suspend fun getSupportedPlans(): List<FanboxCreatorPlan>

    suspend fun getPaidRecords(): List<FanboxPaidRecord>

    suspend fun getUnpaidRecords(): List<FanboxPaidRecord>

    suspend fun getNewsLetters(): List<FanboxNewsLetter>

    suspend fun getBells(
        page: Int = 0,
        skipConvertUnreadNotification: Boolean = false,
        commentOnly: Boolean = false,
    ): PageNumberInfo<FanboxBell>

    suspend fun getCreatorFromQuery(
        query: String,
        page: Int = 0,
    ): PageNumberInfo<FanboxCreatorDetail>

    suspend fun getTagFromQuery(query: String): List<FanboxTag>

    companion object {
        internal const val DEFAULT_LOAD_SIZE = 10
    }
}
