package me.matsumo.fankt.fanbox

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import me.matsumo.fankt.fanbox.datasource.createFanboxCreatorApi
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
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import me.matsumo.fankt.fanbox.repository.FanboxCreatorRepository
import me.matsumo.fankt.fanbox.repository.FanboxPostRepository
import me.matsumo.fankt.fanbox.repository.FanboxSearchRepository
import me.matsumo.fankt.fanbox.repository.FanboxUserRepository

/**
 * Provides access to the pixivFANBOX API.
 *
 * The public [logLevel] maps [FanboxLogLevel.BODY] to an effective info level and
 * [FanboxLogLevel.ALL] to an effective headers-only level. The HTTP logger never receives a
 * raw response body. For allowlisted generated-route errors, a sanitized and bounded diagnostic
 * fragment is retained and logged through a separate path; downloads and unknown routes retain no
 * response fragment.
 *
 * This instance owns every HTTP client it creates. Call [close] after all requests and downloads
 * finish. Public operations started after [close] throw [IllegalStateException]. The underlying
 * engine shutdown completes asynchronously after client close is initiated.
 *
 * Authentication state belongs to the injected [FanboxCookieStorage] and [FanboxTokenStore]. The
 * public constructor creates new in-memory stores for each call by default, so authentication is
 * isolated between instances and is not restored after process recreation. Inject persistent
 * storage explicitly when durability is required. The optional `fanbox-persistence-room` artifact
 * provides platform-explicit Room storage without changing this default. [close] does not close or
 * clear injected stores; close this [Fanbox] before closing its host-owned storage.
 *
 * The v0.1.0 constructor uses [FanboxLogLevel] instead of the HTTP implementation's logging type,
 * changes default authentication durability, and is intentionally source- and binary-incompatible.
 * Consumers must migrate the logging argument and perform a clean rebuild when upgrading.
 */
class Fanbox internal constructor(
    private val dependencies: FanboxDependencies,
    private val clientFactory: FanboxHttpClientFactory,
    private val logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val lifecycle = Job()
    private val formatter = createFanboxJson()
    private val resources = buildResources()

    private val post get() = requireOpen(resources.post)
    private val creator get() = requireOpen(resources.creator)
    private val search get() = requireOpen(resources.search)
    private val user get() = requireOpen(resources.user)
    private val downloadClient get() = requireOpen(resources.downloadClient)

    val cookies get() = requireOpen(dependencies.cookies)

    /** Observes the current CSRF token in this instance's injected [FanboxTokenStore]. */
    val csrfToken get() = requireOpen(dependencies.csrfToken)

    constructor(
        logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        cookieStorage: FanboxCookieStorage = InMemoryFanboxCookieStorage(),
        tokenStore: FanboxTokenStore = InMemoryFanboxTokenStore(),
    ) : this(
        dependencies = createFanboxDependencies(cookieStorage, tokenStore),
        clientFactory = DefaultFanboxHttpClientFactory,
        logLevel = logLevel,
        ioDispatcher = ioDispatcher,
    )

    internal constructor(
        clientFactory: FanboxHttpClientFactory,
        logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        cookieStorage: FanboxCookieStorage = InMemoryFanboxCookieStorage(),
        tokenStore: FanboxTokenStore = InMemoryFanboxTokenStore(),
    ) : this(
        dependencies = createFanboxDependencies(cookieStorage, tokenStore),
        clientFactory = clientFactory,
        logLevel = logLevel,
        ioDispatcher = ioDispatcher,
    )

    private fun buildResources(): FanboxResources {
        val clients = mutableListOf<HttpClient>()

        fun buildOwnedClient(
            source: FanboxDiagnosticSource,
            isEnableContentNegotiation: Boolean,
            isDownloadClient: Boolean = false,
        ): HttpClient {
            return buildHttpClient(
                formatter = formatter,
                cookieStorage = dependencies.cookieStorage,
                source = source,
                csrfTokenProvider = dependencies.getCsrfToken,
                logLevel = logLevel,
                isEnableContentNegotiation = isEnableContentNegotiation,
                isDownloadClient = isDownloadClient,
                clientFactory = clientFactory,
            ).also(clients::add)
        }

        try {
            val apiClient = buildOwnedClient(FanboxDiagnosticSource.LibraryGenerated, true)
            val apiWithoutContentNegotiationClient = buildOwnedClient(
                FanboxDiagnosticSource.LibraryGenerated,
                false,
            )
            val downloadClient = buildOwnedClient(
                source = FanboxDiagnosticSource.Download,
                isEnableContentNegotiation = false,
                isDownloadClient = true,
            )

            val ktorfit = Ktorfit.Builder()
                .baseUrl("https://api.fanbox.cc/")
                .httpClient(apiClient)
                .build()

            val ktorfitWithoutContentNegotiation = Ktorfit.Builder()
                .baseUrl("https://api.fanbox.cc/")
                .httpClient(apiWithoutContentNegotiationClient)
                .build()

            val postApi = ktorfit.createFanboxPostApi()
            val creatorApi = ktorfit.createFanboxCreatorApi()
            val searchApi = ktorfit.createFanboxSearchApi()
            val userApi = ktorfit.createFanboxUserApi()

            val postWithoutContentNegotiation = ktorfitWithoutContentNegotiation.createFanboxPostApi()
            val creatorWithoutContentNegotiation = ktorfitWithoutContentNegotiation.createFanboxCreatorApi()

            val listItemDecoder = FanboxListItemDecoder(formatter, logLevel != FanboxLogLevel.NONE)
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
                downloadClient = downloadClient,
                clients = clients,
            )
        } catch (failure: Throwable) {
            closeClients(clients)?.let(failure::addSuppressed)
            throw failure
        }
    }

    /** Replaces the FANBOX session Cookie and then clears the injected CSRF token on success. */
    suspend fun setFanboxSessionId(sessionId: String) {
        ensureOpen()
        dependencies.overrideFanboxSessionId(sessionId)
        dependencies.clearCsrfToken()
    }

    /**
     * Stores [cookies]. Resetting cookies or supplying `FANBOXSESSID` clears the injected
     * CSRF token before replacement-cookie writes; unrelated additive cookies preserve it.
     *
     * Each record's [FanboxCookieRecord.domain] and [FanboxCookieRecord.hostOnly] fields are the
     * sole authority for Cookie scope. Use [setFanboxSessionId] for the conventional cross-subdomain
     * `FANBOXSESSID` scope. Expired additive records delete their matching identity; expired records
     * are omitted from an atomic replacement.
     */
    suspend fun setCookies(
        cookies: List<FanboxCookieRecord>,
        reset: Boolean = false,
    ) {
        ensureOpen()
        if (reset || cookies.any { cookie -> cookie.name == FANBOX_SESSION_COOKIE_NAME }) {
            dependencies.clearCsrfToken()
        }

        if (reset) {
            dependencies.replaceCookies(cookies)
            return
        }

        for (cookie in cookies) {
            dependencies.addCookie(cookie)
        }
    }

    /**
     * Fetches metadata and stores its CSRF token in the injected token store.
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
     * Streams authenticated media from [url] in bounded, ordered chunks.
     *
     * The URL must use HTTPS and target `fanbox.cc`, one of its subdomains,
     * `pixiv.pximg.net`, or `fanbox.pixiv.net`. The same destination check applies to redirects.
     * The complete port, path, extension, and query are preserved. [onProgress] receives an initial
     * `0f`, then downloaded-byte fractions after the matching [onChunk] callback completes when the
     * response supplies a positive content length. Each chunk is an independent value no larger
     * than the internal read buffer and the next read waits for [onChunk] to return.
     *
     * Callback failures and cancellation propagate unchanged. The response is released before this
     * function returns or throws. Callers writing files should write to a temporary destination and
     * promote it only after this function completes successfully.
     *
     * @throws IllegalArgumentException when [url] or a redirect host is not allowed.
     * @throws FanboxException when the request or response transport fails.
     */
    suspend fun download(
        url: String,
        onProgress: (Float) -> Unit = {},
        onChunk: suspend (ByteArray) -> Unit,
    ) {
        val client = downloadClient
        val validatedUrl = parseFanboxDownloadUrl(url)
        val downloadJob = requireNotNull(currentCoroutineContext()[Job])
        val closeHandle = lifecycle.invokeOnCompletion {
            downloadJob.cancel(CancellationException("Fanbox is closed"))
        }
        try {
            client.prepareGet(validatedUrl.toString()).execute { response ->
                val contentLength = response.headers[HttpHeaders.ContentLength]
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                val channel = normalizeDownloadTransport(response.request) {
                    response.bodyAsChannel()
                }
                val buffer = ByteArray(DOWNLOAD_CHUNK_SIZE)
                var deliveredBytes = 0L

                onProgress(0f)
                while (true) {
                    val read = normalizeDownloadTransport(response.request) {
                        channel.readAvailable(buffer)
                    }
                    if (read < 0) break
                    if (read == 0) continue

                    onChunk(buffer.copyOf(read))
                    deliveredBytes += read
                    if (contentLength != null) {
                        onProgress((deliveredBytes.toDouble() / contentLength).toFloat())
                    }
                }
            }
        } finally {
            closeHandle.dispose()
        }
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
    val downloadClient: HttpClient,
    val clients: List<HttpClient>,
)

private const val DOWNLOAD_CHUNK_SIZE: Int = 8 * 1_024

private suspend inline fun <T> normalizeDownloadTransport(
    request: io.ktor.client.request.HttpRequest,
    block: suspend () -> T,
): T = try {
    block()
} catch (failure: CancellationException) {
    throw failure
} catch (failure: FanboxException) {
    throw failure
} catch (failure: IOException) {
    throw FanboxExceptionFactory.network(request, FanboxDiagnosticSource.Download, failure)
}

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
