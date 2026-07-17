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
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
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
 *
 * This instance owns every [HttpClient] it creates. Call [close] after all requests and deferred
 * [HttpStatement] executions finish. A client returned by [getHttpClient] is shared and owned by
 * this instance; callers must not close it directly. Public operations started after [close]
 * throw [IllegalStateException]. Ktor completes underlying engine shutdown asynchronously after
 * client close is initiated. Callers must not race [close] with requests or another close call.
 */
class Fanbox internal constructor(
    private val dependencies: FanboxDependencies,
    private val clientFactory: FanboxHttpClientFactory,
    private val logLevel: LogLevel = LogLevel.NONE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val lifecycle = Job()
    private val formatter = createFanboxJson()
    private val resources = buildResources()

    private val post get() = requireOpen(resources.post)
    private val creator get() = requireOpen(resources.creator)
    private val search get() = requireOpen(resources.search)
    private val user get() = requireOpen(resources.user)
    private val download get() = requireOpen(resources.download)

    val cookies get() = requireOpen(dependencies.cookies)

    /**
     * Observes the current process-session CSRF token.
     *
     * The value starts as null in a fresh process, is shared by [Fanbox] instances that use the
     * process-local cookie session, and is not persisted across process recreation.
     */
    val csrfToken get() = requireOpen(dependencies.csrfToken)

    constructor(
        logLevel: LogLevel = LogLevel.NONE,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        dependencies = createFanboxDependencies(ioDispatcher),
        clientFactory = DefaultFanboxHttpClientFactory,
        logLevel = logLevel,
        ioDispatcher = ioDispatcher,
    )

    private fun buildResources(): FanboxResources {
        val clients = mutableListOf<HttpClient>()

        fun buildOwnedClient(
            source: FanboxDiagnosticSource,
            isEnableContentNegotiation: Boolean,
        ): HttpClient {
            return buildHttpClient(
                formatter = formatter,
                cookieStorage = dependencies.cookieStorage,
                source = source,
                csrfTokenProvider = dependencies.getCsrfToken,
                logLevel = logLevel,
                isEnableContentNegotiation = isEnableContentNegotiation,
                clientFactory = clientFactory,
            ).also(clients::add)
        }

        try {
            val apiClient = buildOwnedClient(FanboxDiagnosticSource.LibraryGenerated, true)
            val apiWithoutContentNegotiationClient = buildOwnedClient(
                FanboxDiagnosticSource.LibraryGenerated,
                false,
            )
            val downloadClient = buildOwnedClient(FanboxDiagnosticSource.LibraryGenerated, true)

            val ktorfit = Ktorfit.Builder()
                .baseUrl("https://api.fanbox.cc/")
                .httpClient(apiClient)
                .build()

            val ktorfitWithoutContentNegotiation = Ktorfit.Builder()
                .baseUrl("https://api.fanbox.cc/")
                .httpClient(apiWithoutContentNegotiationClient)
                .build()

            val ktorfitDownload = Ktorfit.Builder()
                .baseUrl("https://downloads.fanbox.cc/")
                .httpClient(downloadClient)
                .build()

            val postApi = ktorfit.createFanboxPostApi()
            val creatorApi = ktorfit.createFanboxCreatorApi()
            val searchApi = ktorfit.createFanboxSearchApi()
            val userApi = ktorfit.createFanboxUserApi()
            val downloadApi = ktorfitDownload.createFanboxDownloadApi()

            val postWithoutContentNegotiation = ktorfitWithoutContentNegotiation.createFanboxPostApi()
            val creatorWithoutContentNegotiation = ktorfitWithoutContentNegotiation.createFanboxCreatorApi()

            val listItemDecoder = FanboxListItemDecoder(formatter, logLevel != LogLevel.NONE)
            val postMapper = FanboxPostMapper(listItemDecoder, formatter)
            val creatorMapper = FanboxCreatorMapper(listItemDecoder)
            val searchMapper = me.matsumo.fankt.fanbox.datasource.mapper.FanboxSearchMapper(creatorMapper)
            val userMapper = FanboxUserMapper(postMapper, creatorMapper, listItemDecoder)
            val metadataParser = FanboxMetadataParser(formatter)

            return FanboxResources(
                post = FanboxPostRepository(postApi, postWithoutContentNegotiation, postMapper),
                creator = FanboxCreatorRepository(creatorApi, creatorWithoutContentNegotiation, creatorMapper),
                search = FanboxSearchRepository(searchApi, searchMapper),
                user = FanboxUserRepository(userApi, userMapper, metadataParser),
                download = FanboxDownloadRepository(downloadApi),
                rawClient = buildOwnedClient(FanboxDiagnosticSource.PublicRaw, true),
                rawClientWithoutContentNegotiation = buildOwnedClient(FanboxDiagnosticSource.PublicRaw, false),
                clients = clients,
            )
        } catch (failure: Throwable) {
            closeClients(clients)?.let(failure::addSuppressed)
            throw failure
        }
    }

    /**
     * Returns a shared client for custom requests.
     *
     * The returned client is owned by this [Fanbox] instance. Callers must not close it. Repeated
     * calls with the same [isEnableContentNegotiation] value return the same instance.
     *
     * Failures from requests made with this client always use endpoint `custom-request` and suppress
     * response fragments, even when a request targets a library-owned FANBOX path.
     *
     * @throws IllegalStateException when this [Fanbox] is closed.
     */
    suspend fun getHttpClient(isEnableContentNegotiation: Boolean = true): HttpClient {
        return requireOpen(
            if (isEnableContentNegotiation) {
                resources.rawClient
            } else {
                resources.rawClientWithoutContentNegotiation
            },
        )
    }

    /** Replaces the FANBOX session cookie and clears the process-session CSRF token on success. */
    suspend fun setFanboxSessionId(sessionId: String) {
        ensureOpen()
        dependencies.overrideFanboxSessionId(sessionId)
        dependencies.clearCsrfToken()
    }

    /**
     * Stores [cookies]. Resetting cookies or supplying `FANBOXSESSID` clears the process-session
     * CSRF token before replacement-cookie writes; unrelated additive cookies preserve it.
     */
    suspend fun setCookies(
        cookies: List<Cookie>,
        url: String = "https://www.fanbox.cc",
        reset: Boolean = false,
    ) {
        ensureOpen()
        if (reset) {
            dependencies.clearCookies()
        }

        if (reset || cookies.any { cookie -> cookie.name == FANBOX_SESSION_COOKIE_NAME }) {
            dependencies.clearCsrfToken()
        }

        for (cookie in cookies) {
            dependencies.cookieStorage.addCookie(Url(url), cookie)
        }
    }

    /**
     * Fetches metadata and stores its CSRF token in process memory.
     *
     * Callers must serialize this operation with session or reset-cookie changes. A fresh process
     * and a changed session require a successful refresh before protected requests are started.
     *
     * @throws FanboxException when the request fails.
     */
    suspend fun updateCsrfToken() {
        ensureOpen()
        withContext(ioDispatcher) {
            fetchAndStoreCsrfToken(
                fetchMetadata = ::getMetadata,
                storeToken = dependencies.setCsrfToken,
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
     * Execute the returned statement before [close]. Execution after owner close fails with Ktor's
     * closed-client exception rather than a [FanboxException].
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
     * Execute the returned statement before [close]. Execution after owner close fails with Ktor's
     * closed-client exception rather than a [FanboxException].
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
     * Execute the returned statement before [close]. Execution after owner close fails with Ktor's
     * closed-client exception rather than a [FanboxException].
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

    override fun close() {
        if (!lifecycle.complete()) return
        closeClients(resources.clients)?.let { throw it }
    }

    private fun ensureOpen() {
        check(lifecycle.isActive) { "Fanbox is closed" }
    }

    private fun <T> requireOpen(value: T): T {
        ensureOpen()
        return value
    }
}

private data class FanboxResources(
    val post: FanboxPostRepository,
    val creator: FanboxCreatorRepository,
    val search: FanboxSearchRepository,
    val user: FanboxUserRepository,
    val download: FanboxDownloadRepository,
    val rawClient: HttpClient,
    val rawClientWithoutContentNegotiation: HttpClient,
    val clients: List<HttpClient>,
)

private fun closeClients(clients: List<HttpClient>): Throwable? {
    var failure: Throwable? = null
    for (client in clients) {
        try {
            client.close()
        } catch (closeFailure: Throwable) {
            if (failure == null) {
                failure = closeFailure
            } else {
                failure.addSuppressed(closeFailure)
            }
        }
    }
    return failure
}

internal fun <T> FanboxTolerantResult<T>.notifyMismatches(
    callback: (FanboxListItemSchemaMismatch) -> Unit,
): T {
    mismatches.forEach(callback)
    return value
}

internal suspend fun fetchAndStoreCsrfToken(
    fetchMetadata: suspend () -> FanboxMetaData,
    storeToken: suspend (String) -> Unit,
) {
    val metadata = fetchMetadata()
    storeToken(metadata.csrfToken)
}

private const val FANBOX_SESSION_COOKIE_NAME = "FANBOXSESSID"
