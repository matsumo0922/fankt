package me.matsumo.fankt.fanbox

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.io.IOException
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
import me.matsumo.fankt.fanbox.guest.FanboxGuestDeliveryConfig
import me.matsumo.fankt.fanbox.guest.FanboxGuestHost
import me.matsumo.fankt.fanbox.guest.LoadedGuest
import me.matsumo.fankt.fanbox.guest.ziplineGuestLoader
import me.matsumo.fankt.fanbox.repository.FanboxCreatorRepository
import me.matsumo.fankt.fanbox.repository.FanboxPostRepository
import me.matsumo.fankt.fanbox.repository.FanboxSearchRepository
import me.matsumo.fankt.fanbox.repository.FanboxUserRepository
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor
import me.matsumo.fankt.fanbox.transport.ktor.KtorFanboxRequestExecutor
import okio.ByteString.Companion.toByteString

/**
 * pixivFANBOX API へのアクセスを提供する。
 *
 * [FanboxLogLevel.BODY] は INFO、[FanboxLogLevel.ALL] は HEADERS として扱い、HTTP logger に生の本文を渡さない。
 * API の失敗には認証情報を除去して長さを制限した診断断片を保持し、ダウンロードと未知の route には保持しない。
 *
 * この instance は生成した HTTP client を所有する。
 * リクエストとダウンロードの完了後に [close] を呼ぶ。
 * [close] 後に開始した公開操作は [IllegalStateException] で失敗し、HTTP engine の終了は非同期に完了する。
 *
 * 認証状態は注入された [FanboxCookieStorage] と [FanboxTokenStore] が所有する。
 * デフォルトの保存領域は instance ごとに独立したメモリ上の値で、process 再生成時には復元されない。
 * 永続化には保存領域を明示的に注入し、Room を使う場合は `fanbox-persistence-room` を使用する。
 * [close] は注入された保存領域を終了・消去しないため、保存領域を閉じる前にこの [Fanbox] を閉じる。
 */
// 既存の例外契約を維持する。Result への移行は https://github.com/matsumo0922/fankt/issues/117 で扱う。
// 公開 API の facade として endpoint を集約する。
@Suppress("TooManyFunctions")
class Fanbox internal constructor(
    private val dependencies: FanboxDependencies,
    private val clientFactory: FanboxHttpClientFactory,
    private val loadGuest: suspend (CoroutineDispatcher) -> LoadedGuest,
    private val guestEventListener: (FanboxGuestEvent) -> Unit = {},
    private val logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val lifecycle = Job()
    private val resources = buildResources()

    private val post get() = requireOpen(resources.post)
    private val creator get() = requireOpen(resources.creator)
    private val search get() = requireOpen(resources.search)
    private val user get() = requireOpen(resources.user)
    private val downloadClient get() = requireOpen(resources.downloadClient)

    val cookies get() = requireOpen(dependencies.cookies)

    /** Observes the current CSRF token in this instance's injected [FanboxTokenStore]. */
    val csrfToken get() = requireOpen(dependencies.csrfToken)

    /**
     * Builds FANBOX requests and interprets their responses with the signed Zipline guest.
     *
     * The guest loads in the background from [guestManifestUrl] once this instance is created, and
     * modules already in [guestCache] are not downloaded again. When the manifest cannot be loaded
     * from [guestManifestUrl], the guest last verified into [guestCache] is loaded instead. Requests
     * wait for the load. When neither loads, every request of this instance fails with
     * [FanboxException.GuestFailure].
     *
     * [guestTrustedKeys] maps each trusted signing key name to its Ed25519 public key. A manifest
     * signed by any of them is accepted.
     *
     * [guestEventListener] receives what happens on the guest path. It is called on a background
     * thread, possibly before this constructor returns and possibly from several threads at once, so
     * it must return quickly and must not call [close]. An exception it throws is logged rather than
     * propagated.
     */
    constructor(
        guestManifestUrl: String,
        guestTrustedKeys: Map<String, ByteArray>,
        guestCache: FanboxGuestCache,
        guestEventListener: (FanboxGuestEvent) -> Unit = {},
        logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        cookieStorage: FanboxCookieStorage = InMemoryFanboxCookieStorage(),
        tokenStore: FanboxTokenStore = InMemoryFanboxTokenStore(),
    ) : this(
        dependencies = createFanboxDependencies(cookieStorage, tokenStore),
        clientFactory = DefaultFanboxHttpClientFactory,
        loadGuest = ziplineGuestLoader(
            config = FanboxGuestDeliveryConfig(
                manifestUrl = guestManifestUrl,
                trustedKeys = guestTrustedKeys.mapValues { (_, key) -> key.toByteString() },
                cache = guestCache,
            ),
            clientFactory = DefaultFanboxHttpClientFactory,
        ),
        guestEventListener = guestEventListener,
        logLevel = logLevel,
        ioDispatcher = ioDispatcher,
    )

    internal constructor(
        clientFactory: FanboxHttpClientFactory,
        loadGuest: suspend (CoroutineDispatcher) -> LoadedGuest,
        logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        cookieStorage: FanboxCookieStorage = InMemoryFanboxCookieStorage(),
        tokenStore: FanboxTokenStore = InMemoryFanboxTokenStore(),
        guestEventListener: (FanboxGuestEvent) -> Unit = {},
    ) : this(
        dependencies = createFanboxDependencies(cookieStorage, tokenStore),
        clientFactory = clientFactory,
        loadGuest = loadGuest,
        guestEventListener = guestEventListener,
        logLevel = logLevel,
        ioDispatcher = ioDispatcher,
    )

    private fun buildResources(): FanboxResources {
        val clients = mutableListOf<HttpClient>()

        fun buildOwnedExecutorClient(): HttpClient = buildFanboxExecutorHttpClient(
            cookieStorage = dependencies.cookieStorage,
            csrfTokenProvider = dependencies.getCsrfToken,
            logLevel = logLevel,
            clientFactory = clientFactory,
        ).also(clients::add)

        fun buildOwnedDownloadClient(): HttpClient = buildFanboxDownloadHttpClient(
            cookieStorage = dependencies.cookieStorage,
            csrfTokenProvider = dependencies.getCsrfToken,
            logLevel = logLevel,
            clientFactory = clientFactory,
        ).also(clients::add)

        try {
            val requestExecutor = KtorFanboxRequestExecutor(
                client = buildOwnedExecutorClient(),
                logLevel = logLevel,
            )
            val downloadClient = buildOwnedDownloadClient()
            val diagnosticSink = FanboxDiagnosticSink { message -> Napier.w { message } }
            val guestHost = FanboxGuestHost(
                requestExecutor = requestExecutor,
                diagnosticSink = diagnosticSink,
                eventListener = guestEventListener,
                load = loadGuest,
            )

            return FanboxResources(
                post = FanboxPostRepository(
                    requestExecutor = requestExecutor,
                    diagnosticSink = diagnosticSink,
                    ioDispatcher = ioDispatcher,
                    guestHost = guestHost,
                ),
                creator = FanboxCreatorRepository(
                    requestExecutor = requestExecutor,
                    diagnosticSink = diagnosticSink,
                    ioDispatcher = ioDispatcher,
                ),
                search = FanboxSearchRepository(
                    requestExecutor = requestExecutor,
                    ioDispatcher = ioDispatcher,
                ),
                user = FanboxUserRepository(
                    requestExecutor = requestExecutor,
                    diagnosticSink = diagnosticSink,
                    ioDispatcher = ioDispatcher,
                ),
                requestExecutor = requestExecutor,
                downloadClient = downloadClient,
                clients = clients,
                guestHost = guestHost,
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
    suspend fun getHomePosts(cursor: FanboxCursor?): PageCursorInfo<FanboxPost> = post.getHomePosts(cursor).value

    /**
     * Returns home posts and reports each skipped item before returning.
     *
     * [onItemSchemaMismatch] runs on the caller's coroutine context. An exception from the callback
     * is propagated to the caller.
     */
    suspend fun getHomePosts(cursor: FanboxCursor?, onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit): PageCursorInfo<FanboxPost> = post.getHomePosts(cursor).notifyMismatches(onItemSchemaMismatch)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getSupportedPosts(cursor: FanboxCursor?): PageCursorInfo<FanboxPost> = post.getSupportedPosts(cursor).value

    /** Returns supported posts and reports each skipped item on the caller's coroutine context. */
    suspend fun getSupportedPosts(cursor: FanboxCursor?, onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit): PageCursorInfo<FanboxPost> = post.getSupportedPosts(cursor).notifyMismatches(onItemSchemaMismatch)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorPosts(
        creatorId: FanboxCreatorId,
        cursor: FanboxCursor?,
        nextCursor: FanboxCursor?,
    ): PageCursorInfo<FanboxPost> = post.getCreatorPosts(
        creatorId = creatorId,
        cursor = cursor,
        nextCursor = nextCursor,
    ).value

    /** Returns creator posts and reports each skipped item on the caller's coroutine context. */
    suspend fun getCreatorPosts(
        creatorId: FanboxCreatorId,
        cursor: FanboxCursor?,
        nextCursor: FanboxCursor?,
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): PageCursorInfo<FanboxPost> = post.getCreatorPosts(
        creatorId = creatorId,
        cursor = cursor,
        nextCursor = nextCursor,
    ).notifyMismatches(onItemSchemaMismatch)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorPostsPagination(creatorId: FanboxCreatorId): List<FanboxCursor> = post.getCreatorPostsPagination(creatorId)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getPostDetail(postId: FanboxPostId): FanboxPostDetail = post.getPostDetail(postId)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getPostComment(postId: FanboxPostId, offset: Int): PageOffsetInfo<FanboxComment> = post.getPostComment(postId, offset).value

    /** Returns comments and reports each skipped comment or reply on the caller's coroutine context. */
    suspend fun getPostComment(
        postId: FanboxPostId,
        offset: Int,
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
    ): PageOffsetInfo<FanboxComment> = post.getPostComment(postId, offset).notifyMismatches(onItemSchemaMismatch)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getPostFromQuery(
        query: String,
        creatorId: FanboxCreatorId?,
        page: Int,
    ): PageNumberInfo<FanboxPost> = post.getPostFromQuery(
        query = query,
        creatorId = creatorId,
        page = page,
    )

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun likePost(postId: FanboxPostId) {
        post.likePost(postId)
    }

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun likeComment(commentId: FanboxCommentId) {
        post.likeComment(commentId)
    }

    /**
     * Adds a comment to a post. Pass null for both comment IDs when creating a root comment.
     *
     * @throws FanboxException when the FANBOX request fails.
     */
    suspend fun addComment(
        postId: FanboxPostId,
        rootCommentId: FanboxCommentId?,
        parentCommentId: FanboxCommentId?,
        body: String,
    ) {
        post.addComment(postId, rootCommentId, parentCommentId, body)
    }

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun deleteComment(commentId: FanboxCommentId) {
        post.deleteComment(commentId)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorDetail(creatorId: FanboxCreatorId): FanboxCreatorDetail = creator.getCreatorDetail(creatorId)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getFollowingCreators(): List<FanboxCreatorDetail> = creator.getFollowingCreators().value

    /** Returns followed creators and reports each skipped item on the caller's coroutine context. */
    suspend fun getFollowingCreators(onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit): List<FanboxCreatorDetail> = creator.getFollowingCreators().notifyMismatches(onItemSchemaMismatch)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getFollowingPixivCreators(): List<FanboxCreatorDetail> = creator.getFollowingPixivCreators().value

    /** Returns followed pixiv creators and reports each skipped item on the caller's coroutine context. */
    suspend fun getFollowingPixivCreators(onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit): List<FanboxCreatorDetail> = creator.getFollowingPixivCreators().notifyMismatches(onItemSchemaMismatch)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getRecommendedCreators(): List<FanboxCreatorDetail> = creator.getRecommendedCreators().value

    /** Returns recommended creators and reports each skipped item on the caller's coroutine context. */
    suspend fun getRecommendedCreators(onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit): List<FanboxCreatorDetail> = creator.getRecommendedCreators().notifyMismatches(onItemSchemaMismatch)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorPlans(creatorId: FanboxCreatorId): List<FanboxCreatorPlan> = creator.getCreatorPlans(creatorId).value

    /** Returns creator plans and reports each skipped item on the caller's coroutine context. */
    suspend fun getCreatorPlans(creatorId: FanboxCreatorId, onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit): List<FanboxCreatorPlan> = creator.getCreatorPlans(creatorId).notifyMismatches(onItemSchemaMismatch)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorPlanDetail(creatorId: FanboxCreatorId): FanboxCreatorPlanDetail = creator.getCreatorPlanDetail(creatorId)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getCreatorTags(creatorId: FanboxCreatorId): List<FanboxTag> = creator.getCreatorTags(creatorId)

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun followCreator(userId: FanboxUserId) {
        creator.followCreator(userId)
    }

    /** @throws FanboxException when the FANBOX request fails. */
    suspend fun unfollowCreator(userId: FanboxUserId) {
        creator.unfollowCreator(userId)
    }

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun searchCreators(query: String, page: Int): PageNumberInfo<FanboxCreatorDetail> = search.searchCreators(query, page)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun searchTags(query: String): List<FanboxTag> = search.searchTags(query)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getSupportedPlans(): List<FanboxCreatorPlan> = user.getSupportedPlans()

    /**
     * Returns the decodable supported plans and reports each skipped item before returning.
     *
     * The no-callback overload remains strict and fails the whole request when any plan item cannot
     * be decoded. Use this overload to opt into partial results.
     */
    suspend fun getSupportedPlans(onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit): List<FanboxCreatorPlan> = user.getSupportedPlansTolerant().notifyMismatches(onItemSchemaMismatch)

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getPaidRecords(): List<FanboxPaidRecord> = user.getPaidRecords()

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getUnpaidRecords(): List<FanboxPaidRecord> = user.getUnpaidRecords()

    /** @throws FanboxException when the FANBOX request or response decoding fails. */
    suspend fun getNewsLetters(): List<FanboxNewsLetter> = user.getNewsLetters()

    /**
     * Returns one page of notifications and leaves them unread.
     *
     * @throws FanboxException when the FANBOX request or response decoding fails.
     */
    suspend fun getBells(page: Int): PageNumberInfo<FanboxBell> = getBells(page, markNotificationsRead = false)

    /**
     * Returns one page of notifications.
     *
     * Pass `markNotificationsRead = true` to let FANBOX mark the returned notifications as read
     * while listing them; that conversion cannot be undone through this API.
     *
     * @throws FanboxException when the FANBOX request or response decoding fails.
     */
    suspend fun getBells(page: Int, markNotificationsRead: Boolean): PageNumberInfo<FanboxBell> = user.getBells(page, markNotificationsRead).value

    /**
     * Returns notifications and reports each skipped item on the caller's coroutine context.
     *
     * The returned notifications are left unread.
     */
    suspend fun getBells(page: Int, onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit): PageNumberInfo<FanboxBell> = getBells(
        page = page,
        onItemSchemaMismatch = onItemSchemaMismatch,
        markNotificationsRead = false,
    )

    /**
     * Returns notifications and reports each skipped item on the caller's coroutine context.
     *
     * Pass `markNotificationsRead = true` to let FANBOX mark the returned notifications as read
     * while listing them; that conversion cannot be undone through this API.
     */
    suspend fun getBells(
        page: Int,
        onItemSchemaMismatch: (FanboxListItemSchemaMismatch) -> Unit,
        markNotificationsRead: Boolean,
    ): PageNumberInfo<FanboxBell> = user.getBells(page, markNotificationsRead).notifyMismatches(onItemSchemaMismatch)

    /** @throws FanboxException when homepage metadata cannot be fetched or decoded. */
    suspend fun getMetadata(): FanboxMetaData = user.getMetadata()

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
     * promote it only after this function completes successfully. A caller context does not need a
     * Job. When it has one, cancellation propagates into a dedicated download Job; [close] cancels
     * that download Job without cancelling the caller's parent Job.
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
        val downloadJob = SupervisorJob(currentCoroutineContext()[Job])
        val closeHandle = lifecycle.invokeOnCompletion {
            downloadJob.cancel(CancellationException("Fanbox is closed"))
        }
        val callbacks = DownloadCallbacks(onProgress, onChunk)
        try {
            try {
                withContext(downloadJob) {
                    client.prepareGet(validatedUrl.toString()).execute { response ->
                        deliverDownload(response, callbacks)
                    }
                }
            } catch (failure: Throwable) {
                throw callbacks.failure ?: failure
            }
        } finally {
            downloadJob.complete()
            closeHandle.dispose()
        }
    }

    override fun close() {
        if (!lifecycle.complete()) return

        var failure: Throwable? = null
        try {
            resources.guestHost.close()
        } catch (closeFailure: Throwable) {
            failure = closeFailure
        }
        closeClients(resources.clients)?.let { closeFailure ->
            val firstFailure = failure
            if (firstFailure == null) failure = closeFailure else firstFailure.addSuppressed(closeFailure)
        }
        failure?.let { throw it }
    }

    private fun ensureOpen() {
        check(lifecycle.isActive) { "Fanbox is closed" }
    }

    private fun <T> requireOpen(value: T): T {
        ensureOpen()
        return value
    }
}

/** FANBOX クライアントが所有する repository・transport・guest のリソース。 */
private data class FanboxResources(
    val post: FanboxPostRepository,
    val creator: FanboxCreatorRepository,
    val search: FanboxSearchRepository,
    val user: FanboxUserRepository,
    val requestExecutor: FanboxRequestExecutor,
    val downloadClient: HttpClient,
    val clients: List<HttpClient>,
    val guestHost: FanboxGuestHost,
)

/** ダウンロードの callback が投げた例外を保持し、transport の失敗と区別する。 */
private class DownloadCallbacks(private val onProgress: (Float) -> Unit, private val onChunk: suspend (ByteArray) -> Unit) {
    var failure: Throwable? = null
        private set

    fun report(progress: Float) = captureFailure { onProgress(progress) }

    suspend fun deliver(chunk: ByteArray) = captureFailure { onChunk(chunk) }

    private inline fun captureFailure(block: () -> Unit) {
        try {
            block()
        } catch (callbackFailure: Throwable) {
            failure = callbackFailure
            throw callbackFailure
        }
    }
}

private suspend fun deliverDownload(response: HttpResponse, callbacks: DownloadCallbacks) {
    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it > 0L }
    val channel = normalizeDownloadTransport { response.bodyAsChannel() }
    val buffer = ByteArray(64 * 1_024)
    var deliveredBytes = 0L

    callbacks.report(0f)
    while (true) {
        val chunk = readDownloadChunk(
            buffer = buffer,
            read = { target -> channel.readAvailable(target) },
            closedCause = { channel.closedCause },
            networkFailure = { failure -> FanboxExceptionFactory.downloadNetwork(failure) },
        ) ?: break

        callbacks.deliver(chunk)
        deliveredBytes += chunk.size
        if (contentLength != null) {
            callbacks.report(
                progress = (deliveredBytes.toDouble() / contentLength).toFloat().coerceIn(0f, 1f),
            )
        }
    }
}

private suspend inline fun <T> normalizeDownloadTransport(block: suspend () -> T): T = normalizeDownloadTransportFailure(
    block = block,
    networkFailure = { failure ->
        FanboxExceptionFactory.downloadNetwork(failure)
    },
)

internal suspend inline fun <T> normalizeDownloadTransportFailure(block: suspend () -> T, networkFailure: (IOException) -> FanboxException.Network): T = try {
    block()
} catch (failure: CancellationException) {
    throw failure
} catch (failure: FanboxException) {
    throw failure
} catch (failure: IOException) {
    throw networkFailure(failure)
}

internal suspend inline fun readDownloadChunk(
    buffer: ByteArray,
    read: suspend (ByteArray) -> Int,
    closedCause: () -> Throwable?,
    networkFailure: (IOException) -> FanboxException.Network,
): ByteArray? {
    while (true) {
        val readCount = normalizeDownloadTransportFailure(
            block = { read(buffer) },
            networkFailure = networkFailure,
        )
        if (readCount < 0) {
            normalizeDownloadTransportFailure(
                block = { closedCause()?.let { throw it } },
                networkFailure = networkFailure,
            )
            return null
        }
        if (readCount == 0) {
            yield()
            continue
        }
        return buffer.copyOf(readCount)
    }
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

internal fun <T> FanboxTolerantResult<T>.notifyMismatches(callback: (FanboxListItemSchemaMismatch) -> Unit): T {
    mismatches.forEach(callback)
    return value
}

internal suspend fun fetchAndStoreCsrfToken(fetchMetadata: suspend () -> FanboxMetaData, storeToken: suspend (String) -> Unit) {
    val metadata = fetchMetadata()
    storeToken(metadata.csrfToken)
}
