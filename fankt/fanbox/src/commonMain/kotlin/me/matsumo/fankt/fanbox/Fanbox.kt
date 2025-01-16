package me.matsumo.fankt.fanbox

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.statement.HttpStatement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.datasource.createFanboxCreatorApi
import me.matsumo.fankt.fanbox.datasource.createFanboxDownloadApi
import me.matsumo.fankt.fanbox.datasource.createFanboxPostApi
import me.matsumo.fankt.fanbox.datasource.createFanboxSearchApi
import me.matsumo.fankt.fanbox.datasource.createFanboxUserApi
import me.matsumo.fankt.fanbox.datasource.db.PersistentCookieStorage
import me.matsumo.fankt.fanbox.datasource.db.getFanktDatabase
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxUserMapper
import me.matsumo.fankt.fanbox.domain.FanboxCursor
import me.matsumo.fankt.fanbox.domain.PageCursorInfo
import me.matsumo.fankt.fanbox.domain.PageNumberInfo
import me.matsumo.fankt.fanbox.domain.PageOffsetInfo
import me.matsumo.fankt.fanbox.domain.model.FanboxBell
import me.matsumo.fankt.fanbox.domain.model.FanboxComment
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlan
import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlanDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxMetaData
import me.matsumo.fankt.fanbox.domain.model.FanboxNewsLetter
import me.matsumo.fankt.fanbox.domain.model.FanboxPaidRecord
import me.matsumo.fankt.fanbox.domain.model.FanboxPost
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.FanboxTag
import me.matsumo.fankt.fanbox.domain.model.db.CSRFToken
import me.matsumo.fankt.fanbox.domain.model.db.toCookie
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostItemId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.repository.FanboxCreatorRepository
import me.matsumo.fankt.fanbox.repository.FanboxDownloadRepository
import me.matsumo.fankt.fanbox.repository.FanboxPostRepository
import me.matsumo.fankt.fanbox.repository.FanboxSearchRepository
import me.matsumo.fankt.fanbox.repository.FanboxUserRepository

class Fanbox(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val cookieDao = getFanktDatabase().cookieDao()
    private val tokenDao = getFanktDatabase().tokenDao()

    private val cookieStorage = PersistentCookieStorage(cookieDao)
    private val formatter = Json {
        isLenient = true
        prettyPrint = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    private lateinit var post: FanboxPostRepository
    private lateinit var creator: FanboxCreatorRepository
    private lateinit var search: FanboxSearchRepository
    private lateinit var user: FanboxUserRepository
    private lateinit var download: FanboxDownloadRepository

    init {
        buildKtorfit(null)

        scope.launch {
            tokenDao.getLatestToken().collect {
                buildKtorfit(it)
            }
        }
    }

    val cookies = cookieDao.getAllCookies().map { it.map { cookieEntity -> cookieEntity.toCookie() } }
    val csrfToken = tokenDao.getLatestToken().map { it?.value }

    private fun buildKtorfit(csrfToken: CSRFToken?) {
        val ktorfit = Ktorfit.Builder()
            .baseUrl("https://api.fanbox.cc/")
            .httpClient(buildHttpClient(formatter, cookieStorage, csrfToken, true))
            .build()

        val ktorfitWithoutContentNegotiation = Ktorfit.Builder()
            .baseUrl("https://api.fanbox.cc/")
            .httpClient(buildHttpClient(formatter, cookieStorage, csrfToken, false))
            .build()

        val ktorfitDownload = Ktorfit.Builder()
            .baseUrl("https://downloads.fanbox.cc/")
            .httpClient(buildHttpClient(formatter, cookieStorage, csrfToken, true))
            .build()

        val postApi = ktorfit.createFanboxPostApi()
        val creatorApi = ktorfit.createFanboxCreatorApi()
        val searchApi = ktorfit.createFanboxSearchApi()
        val userApi = ktorfit.createFanboxUserApi()
        val downloadApi = ktorfitDownload.createFanboxDownloadApi()

        val postWithoutContentNegotiation = ktorfitWithoutContentNegotiation.createFanboxPostApi()
        val creatorWithoutContentNegotiation = ktorfitWithoutContentNegotiation.createFanboxCreatorApi()

        val postMapper = FanboxPostMapper()
        val creatorMapper = FanboxCreatorMapper()
        val searchMapper = me.matsumo.fankt.fanbox.datasource.mapper.FanboxSearchMapper(creatorMapper)
        val userMapper = FanboxUserMapper(postMapper, creatorMapper)

        post = FanboxPostRepository(postApi, postWithoutContentNegotiation, postMapper)
        creator = FanboxCreatorRepository(creatorApi, creatorWithoutContentNegotiation, creatorMapper)
        search = FanboxSearchRepository(searchApi, searchMapper)
        user = FanboxUserRepository(userApi, userMapper)
        download = FanboxDownloadRepository(downloadApi)
    }

    suspend fun setFanboxSessionId(sessionId: String) {
        cookieStorage.overrideFanboxSessionId(sessionId)
    }

    suspend fun updateCsrfToken() {
        withContext(ioDispatcher) {
            tokenDao.insert(
                CSRFToken(
                    value = getMetadata().csrfToken,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }
    }

    suspend fun getHomePosts(cursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getHomePosts(cursor)
    }

    suspend fun getSupportedPosts(cursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getSupportedPosts(cursor)
    }

    suspend fun getCreatorPosts(creatorId: FanboxCreatorId, cursor: FanboxCursor?, nextCursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getCreatorPosts(creatorId, cursor, nextCursor)
    }

    suspend fun getPostDetail(postId: FanboxPostId): FanboxPostDetail {
        return post.getPostDetail(postId)
    }

    suspend fun getPostComment(postId: FanboxPostId, offset: Int): PageOffsetInfo<FanboxComment> {
        return post.getPostComment(postId, offset)
    }

    suspend fun getPostFromQuery(query: String, creatorId: FanboxCreatorId?, page: Int): PageNumberInfo<FanboxPost> {
        return post.getPostFromQuery(query, creatorId, page)
    }

    suspend fun likePost(postId: FanboxPostId) {
        post.likePost(postId)
    }

    suspend fun addComment(postId: FanboxPostId, rootCommentId: FanboxCommentId, parentCommentId: FanboxCommentId, body: String) {
        post.addComment(postId, rootCommentId, parentCommentId, body)
    }

    suspend fun deleteComment(commentId: FanboxCommentId) {
        post.deleteComment(commentId)
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

    suspend fun followCreator(userId: FanboxUserId) {
        creator.followCreator(userId)
    }

    suspend fun unfollowCreator(userId: FanboxUserId) {
        creator.unfollowCreator(userId)
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

    suspend fun getMetadata(): FanboxMetaData {
        return user.getMetadata(formatter)
    }

    suspend fun downloadPostImage(
        postId: FanboxPostId,
        itemId: FanboxPostItemId,
        onDownload: (Float) -> Unit,
    ): HttpStatement {
        return download.downloadPostImage(postId, itemId, onDownload)
    }

    suspend fun downloadPostThumbnailImage(
        postId: FanboxPostId,
        itemId: FanboxPostItemId,
        onDownload: (Float) -> Unit,
    ): HttpStatement {
        return download.downloadPostThumbnailImage(postId, itemId, onDownload)
    }
}
