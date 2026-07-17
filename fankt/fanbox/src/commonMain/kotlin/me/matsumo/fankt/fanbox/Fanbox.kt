package me.matsumo.fankt.fanbox

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.statement.HttpStatement
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import me.matsumo.fankt.fanbox.datasource.createFanboxCreatorApi
import me.matsumo.fankt.fanbox.datasource.createFanboxDownloadApi
import me.matsumo.fankt.fanbox.datasource.createFanboxPostApi
import me.matsumo.fankt.fanbox.datasource.createFanboxSearchApi
import me.matsumo.fankt.fanbox.datasource.createFanboxUserApi
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
class Fanbox internal constructor(
    private val dependencies: FanboxDependencies,
    private val clientFactory: FanboxHttpClientFactory,
    private val logLevel: LogLevel = LogLevel.NONE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val formatter = createFanboxJson()
    private val repositories = buildRepositories()

    private val post = repositories.post
    private val creator = repositories.creator
    private val search = repositories.search
    private val user = repositories.user
    private val download = repositories.download

    val cookies = dependencies.cookies
    val csrfToken = dependencies.csrfToken

    constructor(
        logLevel: LogLevel = LogLevel.NONE,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        dependencies = createFanboxDependencies(ioDispatcher),
        clientFactory = DefaultFanboxHttpClientFactory,
        logLevel = logLevel,
        ioDispatcher = ioDispatcher,
    )

    private fun buildRepositories(): FanboxRepositories {
        val ktorfit = Ktorfit.Builder()
            .baseUrl("https://api.fanbox.cc/")
            .httpClient(
                buildHttpClient(
                    formatter,
                    dependencies.cookieStorage,
                    FanboxDiagnosticSource.LibraryGenerated,
                    dependencies.getLatestToken,
                    logLevel,
                    true,
                    clientFactory,
                ),
            )
            .build()

        val ktorfitWithoutContentNegotiation = Ktorfit.Builder()
            .baseUrl("https://api.fanbox.cc/")
            .httpClient(
                buildHttpClient(
                    formatter,
                    dependencies.cookieStorage,
                    FanboxDiagnosticSource.LibraryGenerated,
                    dependencies.getLatestToken,
                    logLevel,
                    false,
                    clientFactory,
                ),
            )
            .build()

        val ktorfitDownload = Ktorfit.Builder()
            .baseUrl("https://downloads.fanbox.cc/")
            .httpClient(
                buildHttpClient(
                    formatter,
                    dependencies.cookieStorage,
                    FanboxDiagnosticSource.LibraryGenerated,
                    dependencies.getLatestToken,
                    logLevel,
                    true,
                    clientFactory,
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

        val listItemDecoder = FanboxListItemDecoder(formatter, logLevel != LogLevel.NONE)
        val postMapper = FanboxPostMapper(listItemDecoder)
        val creatorMapper = FanboxCreatorMapper(listItemDecoder)
        val searchMapper = me.matsumo.fankt.fanbox.datasource.mapper.FanboxSearchMapper(creatorMapper)
        val userMapper = FanboxUserMapper(postMapper, creatorMapper, listItemDecoder)
        val metadataParser = FanboxMetadataParser(formatter)

        return FanboxRepositories(
            post = FanboxPostRepository(postApi, postWithoutContentNegotiation, postMapper),
            creator = FanboxCreatorRepository(creatorApi, creatorWithoutContentNegotiation, creatorMapper),
            search = FanboxSearchRepository(searchApi, searchMapper),
            user = FanboxUserRepository(userApi, userMapper, metadataParser),
            download = FanboxDownloadRepository(downloadApi),
        )
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
            cookieStorage = dependencies.cookieStorage,
            source = FanboxDiagnosticSource.PublicRaw,
            csrfTokenProvider = dependencies.getLatestToken,
            logLevel = logLevel,
            isEnableContentNegotiation = isEnableContentNegotiation,
            clientFactory = clientFactory,
        )
    }

    suspend fun setFanboxSessionId(sessionId: String) {
        dependencies.overrideFanboxSessionId(sessionId)
    }

    suspend fun setCookies(
        cookies: List<Cookie>,
        url: String = "https://www.fanbox.cc",
        reset: Boolean = false,
    ) {
        if (reset) {
            dependencies.clearCookies()
        }

        for (cookie in cookies) {
            dependencies.cookieStorage.addCookie(Url(url), cookie)
        }
    }

    /** Fetches metadata and stores its CSRF token. @throws FanboxException when the request fails. */
    suspend fun updateCsrfToken() {
        withContext(ioDispatcher) {
            fetchAndStoreCsrfToken(
                fetchMetadata = ::getMetadata,
                storeToken = dependencies.insertToken,
                nowEpochMilliseconds = { Clock.System.now().toEpochMilliseconds() },
            )
        }
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getHomePosts(cursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getHomePosts(cursor).value
    }

    /**
     * Returns home posts and reports each skipped item before returning.
     *
     * [onItemSchemaMismatch] runs on the caller's coroutine context. An exception from the callback
     * is propagated to the caller.
     */
    suspend fun getHomePosts(
        cursor: FanboxCursor?,
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): PageCursorInfo<FanboxPost> {
        return post.getHomePosts(cursor).notifyMismatches(onItemSchemaMismatch)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getSupportedPosts(cursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getSupportedPosts(cursor).value
    }

    /** Returns supported posts and reports each skipped item on the caller's coroutine context. */
    suspend fun getSupportedPosts(
        cursor: FanboxCursor?,
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): PageCursorInfo<FanboxPost> {
        return post.getSupportedPosts(cursor).notifyMismatches(onItemSchemaMismatch)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorPosts(creatorId: FanboxCreatorId, cursor: FanboxCursor?, nextCursor: FanboxCursor?): PageCursorInfo<FanboxPost> {
        return post.getCreatorPosts(creatorId, cursor, nextCursor).value
    }

    /** Returns creator posts and reports each skipped item on the caller's coroutine context. */
    suspend fun getCreatorPosts(
        creatorId: FanboxCreatorId,
        cursor: FanboxCursor?,
        nextCursor: FanboxCursor?,
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): PageCursorInfo<FanboxPost> {
        return post.getCreatorPosts(creatorId, cursor, nextCursor).notifyMismatches(onItemSchemaMismatch)
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
        return post.getPostComment(postId, offset).value
    }

    /** Returns comments and reports each skipped comment or reply on the caller's coroutine context. */
    suspend fun getPostComment(
        postId: FanboxPostId,
        offset: Int,
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): PageOffsetInfo<FanboxComment> {
        return post.getPostComment(postId, offset).notifyMismatches(onItemSchemaMismatch)
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
        return creator.getFollowingCreators().value
    }

    /** Returns followed creators and reports each skipped item on the caller's coroutine context. */
    suspend fun getFollowingCreators(
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): List<FanboxCreatorDetail> {
        return creator.getFollowingCreators().notifyMismatches(onItemSchemaMismatch)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getFollowingPixivCreators(): List<FanboxCreatorDetail> {
        return creator.getFollowingPixivCreators().value
    }

    /** Returns followed pixiv creators and reports each skipped item on the caller's coroutine context. */
    suspend fun getFollowingPixivCreators(
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): List<FanboxCreatorDetail> {
        return creator.getFollowingPixivCreators().notifyMismatches(onItemSchemaMismatch)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getRecommendedCreators(): List<FanboxCreatorDetail> {
        return creator.getRecommendedCreators().value
    }

    /** Returns recommended creators and reports each skipped item on the caller's coroutine context. */
    suspend fun getRecommendedCreators(
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): List<FanboxCreatorDetail> {
        return creator.getRecommendedCreators().notifyMismatches(onItemSchemaMismatch)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorPlans(creatorId: FanboxCreatorId): List<FanboxCreatorPlan> {
        return creator.getCreatorPlans(creatorId).value
    }

    /** Returns creator plans and reports each skipped item on the caller's coroutine context. */
    suspend fun getCreatorPlans(
        creatorId: FanboxCreatorId,
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): List<FanboxCreatorPlan> {
        return creator.getCreatorPlans(creatorId).notifyMismatches(onItemSchemaMismatch)
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

    /**
     * Returns the decodable supported plans and reports each skipped item before returning.
     *
     * The no-callback overload remains strict and fails the whole request when any plan item cannot
     * be decoded. Use this overload to opt into partial results.
     */
    suspend fun getSupportedPlans(
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): List<FanboxCreatorPlan> {
        return user.getSupportedPlansTolerant().notifyMismatches(onItemSchemaMismatch)
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
        return user.getBells(page).value
    }

    /** Returns notifications and reports each skipped item on the caller's coroutine context. */
    suspend fun getBells(
        page: Int,
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): PageNumberInfo<FanboxBell> {
        return user.getBells(page).notifyMismatches(onItemSchemaMismatch)
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

private data class FanboxRepositories(
    val post: FanboxPostRepository,
    val creator: FanboxCreatorRepository,
    val search: FanboxSearchRepository,
    val user: FanboxUserRepository,
    val download: FanboxDownloadRepository,
)

internal fun <T> FanboxTolerantResult<T>.notifyMismatches(
    callback: (FanboxListItemSchemaMismatch) -> Unit,
): T {
    mismatches.forEach(callback)
    return value
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
