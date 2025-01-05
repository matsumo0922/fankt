package me.matsumo.fankt

import de.jensklingenberg.ktorfit.Ktorfit
import me.matsumo.fankt.datasource.createFanboxCreatorApi
import me.matsumo.fankt.datasource.createFanboxPostApi
import me.matsumo.fankt.datasource.createFanboxSearchApi
import me.matsumo.fankt.datasource.createFanboxUserApi
import me.matsumo.fankt.datasource.db.PersistentCookieStorage
import me.matsumo.fankt.datasource.db.getCookieDatabase
import me.matsumo.fankt.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.datasource.mapper.FanboxSearchMapper
import me.matsumo.fankt.datasource.mapper.FanboxUserMapper
import me.matsumo.fankt.domain.FanboxCursor
import me.matsumo.fankt.domain.PageCursorInfo
import me.matsumo.fankt.domain.PageNumberInfo
import me.matsumo.fankt.domain.model.FanboxBell
import me.matsumo.fankt.domain.model.FanboxComment
import me.matsumo.fankt.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.domain.model.FanboxCreatorPlanDetail
import me.matsumo.fankt.domain.model.FanboxNewsLetter
import me.matsumo.fankt.domain.model.FanboxPaidRecord
import me.matsumo.fankt.domain.model.FanboxPost
import me.matsumo.fankt.domain.model.FanboxPostDetail
import me.matsumo.fankt.domain.model.FanboxTag
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxPostId
import me.matsumo.fankt.repository.FanboxCreatorRepository
import me.matsumo.fankt.repository.FanboxPostRepository
import me.matsumo.fankt.repository.FanboxSearchRepository
import me.matsumo.fankt.repository.FanboxUserRepository

class Fanbox {

    private val cookieStorage = PersistentCookieStorage(getCookieDatabase().cookieDao())
    private val ktorfit = Ktorfit.Builder()
        .baseUrl("https://api.fanbox.cc/")
        .httpClient(buildHttpClient(true, cookieStorage))
        .build()

    private val disabledContentNegotiationKtorfit = Ktorfit.Builder()
        .baseUrl("https://api.fanbox.cc/")
        .httpClient(buildHttpClient(false, cookieStorage))
        .build()

    private val postApi = ktorfit.createFanboxPostApi()
    private val creatorApi = ktorfit.createFanboxCreatorApi()
    private val searchApi = ktorfit.createFanboxSearchApi()
    private val userApi = ktorfit.createFanboxUserApi()

    private val postWithoutContentNegotiation = disabledContentNegotiationKtorfit.createFanboxPostApi()
    private val creatorWithoutContentNegotiation = disabledContentNegotiationKtorfit.createFanboxCreatorApi()

    private val postMapper = FanboxPostMapper()
    private val creatorMapper = FanboxCreatorMapper()
    private val searchMapper = FanboxSearchMapper(creatorMapper)
    private val userMapper = FanboxUserMapper(postMapper, creatorMapper)

    private val post = FanboxPostRepository(postApi, postWithoutContentNegotiation, postMapper)
    private val creator = FanboxCreatorRepository(creatorApi, creatorWithoutContentNegotiation, creatorMapper)
    private val search = FanboxSearchRepository(searchApi, searchMapper)
    private val user = FanboxUserRepository(userApi, userMapper)

    suspend fun setFanboxSessionId(sessionId: String) {
        cookieStorage.overrideFanboxSessionId(sessionId)
    }

    suspend fun getHomePosts(cursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getHomePosts(cursor)
    }

    suspend fun getSupportedPosts(cursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getSupportedPosts(cursor)
    }

    suspend fun getCreatorPosts(creatorId: FanboxCreatorId, cursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getCreatorPosts(creatorId, cursor)
    }

    suspend fun getPostDetail(postId: FanboxPostId): FanboxPostDetail {
        return post.getPostDetail(postId)
    }

    suspend fun getPostComment(postId: FanboxPostId, offset: Int): PageCursorInfo<FanboxComment> {
        return post.getPostComment(postId, offset)
    }

    suspend fun getPostFromQuery(query: String, creatorId: FanboxCreatorId?, page: Int): PageCursorInfo<FanboxPost> {
        return post.getPostFromQuery(query, creatorId, page)
    }

    suspend fun likePost(postId: FanboxPostId) {
        post.likePost(postId)
    }

    suspend fun addComment(postId: FanboxPostId, text: String) {
        post.addComment(postId, text)
    }

    suspend fun deleteComment(postId: FanboxPostId, commentId: String) {
        post.deleteComment(postId, commentId)
    }

    suspend fun getCreatorDetail(creatorId: FanboxCreatorId): FanboxCreatorDetail {
        return creator.getCreatorDetail(creatorId)
    }

    suspend fun getFollowingCreators(): List<FanboxCreatorDetail> {
        return creator.getFollowingCreators()
    }

    suspend fun getFollowingPixivCreators(): List<FanboxCreatorDetail> {
        return creator.getFollowingPixivCreators()
    }

    suspend fun getRecommendedCreators(): List<FanboxCreatorDetail> {
        return creator.getRecommendedCreators()
    }

    suspend fun getCreatorPlans(creatorId: FanboxCreatorId): List<FanboxCreatorPlan> {
        return creator.getCreatorPlans(creatorId)
    }

    suspend fun getCreatorPlanDetail(creatorId: FanboxCreatorId): FanboxCreatorPlanDetail {
        return creator.getCreatorPlanDetail(creatorId)
    }

    suspend fun getCreatorTags(creatorId: FanboxCreatorId): List<FanboxTag> {
        return creator.getCreatorTags(creatorId)
    }

    suspend fun followCreator(creatorId: FanboxCreatorId) {
        creator.followCreator(creatorId)
    }

    suspend fun unfollowCreator(creatorId: FanboxCreatorId) {
        creator.unfollowCreator(creatorId)
    }

    suspend fun searchCreators(query: String): PageNumberInfo<FanboxCreatorDetail> {
        return search.searchCreators(query)
    }

    suspend fun searchTags(query: String): List<FanboxTag> {
        return search.searchTags(query)
    }

    suspend fun getSupportedPlans(): List<FanboxCreatorPlan> {
        return user.getSupportedPlans()
    }

    suspend fun getPaidRecords(): List<FanboxPaidRecord> {
        return user.getPaidRecords()
    }

    suspend fun getUnpaidRecords(): List<FanboxPaidRecord> {
        return user.getUnpaidRecords()
    }

    suspend fun getNewsLetters(): List<FanboxNewsLetter> {
        return user.getNewsLetters()
    }

    suspend fun getBells(page: Int): PageNumberInfo<FanboxBell> {
        return user.getBells(page)
    }
}