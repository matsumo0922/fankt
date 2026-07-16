package me.matsumo.fankt.fanbox

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.statement.HttpStatement
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
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
import me.matsumo.fankt.fanbox.datasource.parser.FanboxMetadataParser
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

/**
 * Provides access to the pixivFANBOX API.
 *
 * The public [logLevel] maps [LogLevel.BODY] to an effective [LogLevel.INFO] level and
 * [LogLevel.ALL] to an effective [LogLevel.HEADERS] level. The Logging plugin never receives a
 * raw response body. For allowlisted generated-route errors, a sanitized and bounded diagnostic
 * fragment is retained and logged through a separate path; custom requests and unknown routes
 * retain no response fragment.
 */
class Fanbox(
    private val logLevel: LogLevel = LogLevel.NONE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val cookieDao = getFanktDatabase().cookieDao()
    private val tokenDao = getFanktDatabase().tokenDao()

    private val cookieStorage = PersistentCookieStorage(cookieDao)
    private val formatter = createFanboxJson()

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
            .httpClient(
                buildHttpClient(
                    formatter,
                    cookieStorage,
                    FanboxDiagnosticSource.LibraryGenerated,
                    csrfToken,
                    logLevel,
                    true,
                ),
            )
            .build()

        val ktorfitWithoutContentNegotiation = Ktorfit.Builder()
            .baseUrl("https://api.fanbox.cc/")
            .httpClient(
                buildHttpClient(
                    formatter,
                    cookieStorage,
                    FanboxDiagnosticSource.LibraryGenerated,
                    csrfToken,
                    logLevel,
                    false,
                ),
            )
            .build()

        val ktorfitDownload = Ktorfit.Builder()
            .baseUrl("https://downloads.fanbox.cc/")
            .httpClient(
                buildHttpClient(
                    formatter,
                    cookieStorage,
                    FanboxDiagnosticSource.LibraryGenerated,
                    csrfToken,
                    logLevel,
                    true,
                ),
            )
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
        val metadataParser = FanboxMetadataParser(formatter)

        post = FanboxPostRepository(postApi, postWithoutContentNegotiation, postMapper)
        creator = FanboxCreatorRepository(creatorApi, creatorWithoutContentNegotiation, creatorMapper)
        search = FanboxSearchRepository(searchApi, searchMapper)
        user = FanboxUserRepository(userApi, userMapper, metadataParser)
        download = FanboxDownloadRepository(downloadApi)
    }

    /**
     * Returns a separate client for custom requests.
     *
     * Failures from requests made with this client always use endpoint `custom-request` and suppress
     * response fragments, even when a request targets a library-owned FANBOX path.
     */
    suspend fun getHttpClient(isEnableContentNegotiation: Boolean = true): HttpClient {
        return buildHttpClient(
            formatter = formatter,
            cookieStorage = cookieStorage,
            source = FanboxDiagnosticSource.PublicRaw,
            csrfToken = tokenDao.getLatestToken().first(),
            logLevel = logLevel,
            isEnableContentNegotiation = isEnableContentNegotiation,
        )
    }

    suspend fun setFanboxSessionId(sessionId: String) {
        cookieStorage.overrideFanboxSessionId(sessionId)
    }

    suspend fun setCookies(
        cookies: List<Cookie>,
        url: String = "https://www.fanbox.cc",
        reset: Boolean = false,
    ) {
        if (reset) {
            cookieStorage.clear()
        }

        for (cookie in cookies) {
            cookieStorage.addCookie(Url(url), cookie)
        }
    }

    /** Fetches metadata and stores its CSRF token. @throws FanboxException when the request fails. */
    suspend fun updateCsrfToken() {
        withContext(ioDispatcher) {
            fetchAndStoreCsrfToken(
                fetchMetadata = ::getMetadata,
                storeToken = tokenDao::insert,
                nowEpochMilliseconds = { Clock.System.now().toEpochMilliseconds() },
            )
        }
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getHomePosts(cursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getHomePosts(cursor)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getSupportedPosts(cursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getSupportedPosts(cursor)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorPosts(creatorId: FanboxCreatorId, cursor: FanboxCursor?, nextCursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getCreatorPosts(creatorId, cursor, nextCursor)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorPostsPagination(creatorId: FanboxCreatorId): List<FanboxCursor> {
        return post.getCreatorPostsPagination(creatorId)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getPostDetail(postId: FanboxPostId): FanboxPostDetail {
        return post.getPostDetail(postId)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getPostComment(postId: FanboxPostId, offset: Int): PageOffsetInfo<FanboxComment> {
        return post.getPostComment(postId, offset)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getPostFromQuery(query: String, creatorId: FanboxCreatorId?, page: Int): PageNumberInfo<FanboxPost> {
        return post.getPostFromQuery(query, creatorId, page)
    }

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun likePost(postId: FanboxPostId) {
        post.likePost(postId)
    }

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun likeComment(commentId: FanboxCommentId) {
        post.likeComment(commentId)
    }

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun addComment(postId: FanboxPostId, rootCommentId: FanboxCommentId, parentCommentId: FanboxCommentId, body: String) {
        post.addComment(postId, rootCommentId, parentCommentId, body)
    }

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun deleteComment(commentId: FanboxCommentId) {
        post.deleteComment(commentId)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorDetail(creatorId: FanboxCreatorId): FanboxCreatorDetail {
        return creator.getCreatorDetail(creatorId)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getFollowingCreators(): List<FanboxCreatorDetail> {
        return creator.getFollowingCreators()
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getFollowingPixivCreators(): List<FanboxCreatorDetail> {
        return creator.getFollowingPixivCreators()
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getRecommendedCreators(): List<FanboxCreatorDetail> {
        return creator.getRecommendedCreators()
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorPlans(creatorId: FanboxCreatorId): List<FanboxCreatorPlan> {
        return creator.getCreatorPlans(creatorId)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorPlanDetail(creatorId: FanboxCreatorId): FanboxCreatorPlanDetail {
        return creator.getCreatorPlanDetail(creatorId)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorTags(creatorId: FanboxCreatorId): List<FanboxTag> {
        return creator.getCreatorTags(creatorId)
    }

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun followCreator(userId: FanboxUserId) {
        creator.followCreator(userId)
    }

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun unfollowCreator(userId: FanboxUserId) {
        creator.unfollowCreator(userId)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun searchCreators(query: String, page: Int): PageNumberInfo<FanboxCreatorDetail> {
        return search.searchCreators(query, page)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun searchTags(query: String): List<FanboxTag> {
        return search.searchTags(query)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getSupportedPlans(): List<FanboxCreatorPlan> {
        return user.getSupportedPlans()
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getPaidRecords(): List<FanboxPaidRecord> {
        return user.getPaidRecords()
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getUnpaidRecords(): List<FanboxPaidRecord> {
        return user.getUnpaidRecords()
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getNewsLetters(): List<FanboxNewsLetter> {
        return user.getNewsLetters()
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getBells(page: Int): PageNumberInfo<FanboxBell> {
        return user.getBells(page)
    }

    /** @throws FanboxException when homepage metadata cannot be fetched or decoded. */
    suspend fun getMetadata(): FanboxMetaData {
        return user.getMetadata()
    }

    /**
     * Creates a deferred file request.
     *
     * @throws FanboxException when the returned [HttpStatement] is executed and the request fails.
     */
    suspend fun downloadPostFile(
        postId: FanboxPostId,
        itemId: FanboxPostItemId,
        onDownload: (Float) -> Unit,
    ): HttpStatement {
        return download.downloadPostFile(postId, itemId, onDownload)
    }

    /**
     * Creates a deferred image request.
     *
     * @throws FanboxException when the returned [HttpStatement] is executed and the request fails.
     */
    suspend fun downloadPostImage(
        postId: FanboxPostId,
        itemId: FanboxPostItemId,
        onDownload: (Float) -> Unit,
    ): HttpStatement {
        return download.downloadPostImage(postId, itemId, onDownload)
    }

    /**
     * Creates a deferred thumbnail request.
     *
     * @throws FanboxException when the returned [HttpStatement] is executed and the request fails.
     */
    suspend fun downloadPostThumbnailImage(
        postId: FanboxPostId,
        itemId: FanboxPostItemId,
        onDownload: (Float) -> Unit,
    ): HttpStatement {
        return download.downloadPostThumbnailImage(postId, itemId, onDownload)
    }
}

internal suspend fun fetchAndStoreCsrfToken(
    fetchMetadata: suspend () -> FanboxMetaData,
    storeToken: suspend (CSRFToken) -> Unit,
    nowEpochMilliseconds: () -> Long,
) {
    val metadata = fetchMetadata()
    storeToken(
        CSRFToken(
            value = metadata.csrfToken,
            createdAt = nowEpochMilliseconds(),
        ),
    )
}
