package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.Zipline
import app.cash.zipline.ZiplineManifest
import app.cash.zipline.loader.FreshnessChecker
import app.cash.zipline.loader.LoadResult
import app.cash.zipline.loader.ManifestVerifier.Builder
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.ZiplineLoader
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.FanboxGuestCache
import me.matsumo.fankt.fanbox.FanboxHttpClientFactory
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.response.FanboxDiagnostics
import me.matsumo.fankt.fanbox.transport.FanboxRawResponse
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.use
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** guest manifest の配信先・検証鍵・キャッシュの設定。 */
internal class FanboxGuestDeliveryConfig(
    val manifestUrl: String,
    val trustedKeyName: String,
    val trustedEd25519PublicKey: ByteString,
    val cache: FanboxGuestCache,
) {
    init {
        require(Url(manifestUrl).protocol == URLProtocol.HTTPS) { "Guest manifest URL must use HTTPS: $manifestUrl" }
        require(trustedEd25519PublicKey.size == 32) { "Guest Ed25519 public key must contain 32 bytes" }
    }
}

/**
 * Zipline guest を背景でロードし、ロード済みの guest を呼び出す。
 *
 * 呼び出しはロードの完了を待たない。guest が無い間や guest が失敗した呼び出しでは `null` を返し、呼び出し側が
 * 組み込みの request 組み立てと parser で処理する。
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal class FanboxGuestHost(
    private val diagnosticSink: FanboxDiagnosticSink,
    load: suspend (CoroutineDispatcher) -> LoadedGuest,
) : AutoCloseable {
    // Zipline instances are thread-confined; limited parallelism does not guarantee one thread.
    private val dispatcher = newSingleThreadContext("FanboxZipline")

    @Volatile
    private var guest: LoadedGuest? = null

    private val loading = CoroutineScope(dispatcher).launch {
        guest = try {
            load(dispatcher)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            fallBack("load", failure.describe())
        }
    }

    /** 背景のロードが終わるまで待つ。 */
    suspend fun awaitLoad() = loading.join()

    /** guest で `post.info` の request を組み立てる。 */
    suspend fun buildPostDetailRequest(postId: FanboxPostId): RequestDescriptor? = call("buildPostDetailRequest") {
        buildPostDetailRequest(postId.value)
    }

    /** guest で `post.info` の response を解析する。 */
    suspend fun parsePostDetail(response: FanboxRawResponse): FanboxPostDetail? {
        val result = call("parsePostDetail") { parsePostDetail(response.bodyText, response.statusCode) }

        return when (result) {
            is GuestParseResult.Success -> result.postDetail
            is GuestParseResult.SchemaMismatch -> fallBack("parsePostDetail", "schema mismatch: ${result.diagnostic}")
            is GuestParseResult.GuestFailure -> fallBack("parsePostDetail", result.diagnostic)
            null -> null
        }
    }

    // Exception rather than a list of Zipline types: a bridge call can also fail with
    // SerializationException or ZiplineApiMismatchException, which share no supertype with the rest.
    private suspend fun <T> call(operation: String, block: FanboxGuestService.() -> T): T? {
        if (guest == null) return null

        return try {
            withContext(dispatcher) { guest?.service?.block() }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            fallBack(operation, failure.describe())
        }
    }

    private fun fallBack(operation: String, reason: String): Nothing? {
        diagnosticSink.report("Zipline guest $operation failed; using built-in path (${FanboxDiagnostics.sanitizeFragment(reason)})")
        return null
    }

    override fun close() {
        try {
            runBlocking(dispatcher) {
                loading.cancelAndJoin()
                val closing = guest
                guest = null
                closing?.zipline?.close()
            }
        } finally {
            dispatcher.close()
        }
    }
}

/** ロード済みの Zipline と guest service の組。 */
internal data class LoadedGuest(
    val service: FanboxGuestService,
    val zipline: Zipline? = null,
)

/** 署名を検証した配信 bundle から guest をロードする。[dispatcher] の上で呼び出す。 */
internal suspend fun loadZiplineGuest(
    config: FanboxGuestDeliveryConfig,
    dispatcher: CoroutineDispatcher,
    httpClient: ZiplineHttpClient,
): LoadedGuest {
    val manifestVerifier = Builder()
        .addEd25519(name = config.trustedKeyName, trustedKey = config.trustedEd25519PublicKey)
        .build()

    // Zipline verifies a cached manifest before checking its freshness and gives up when that fails,
    // so a cache written under another key would block every load. One directory per key avoids it.
    val cacheDirectoryName = Buffer()
        .writeUtf8(config.trustedKeyName)
        .write(config.trustedEd25519PublicKey)
        .sha256()
        .hex()

    return config.cache.open(cacheDirectoryName, 10L * 1024 * 1024).use { cache ->
        val result = ZiplineLoader(dispatcher, manifestVerifier, httpClient)
            .withCache(cache)
            .loadOnce(applicationName = "fanbox-guest", freshnessChecker = AlwaysStale, manifestUrl = config.manifestUrl)

        when (result) {
            is LoadResult.Success -> LoadedGuest(result.zipline.take(FANBOX_GUEST_SERVICE_NAME), result.zipline)
            is LoadResult.Failure -> throw result.exception
        }
    }
}

/** guest bundle の取得に使う HTTP client を生成する。[requestTimeout] は各リクエストの期限。 */
internal fun createGuestHttpClient(
    clientFactory: FanboxHttpClientFactory,
    requestTimeout: Duration = 10.seconds,
): HttpClient = clientFactory.create {
    expectSuccess = true
    followRedirects = false
    install(HttpTimeout) { requestTimeoutMillis = requestTimeout.inWholeMilliseconds }
}

/** Zipline loader の HTTP 通信を Ktor へ橋渡しする。 */
internal class KtorZiplineHttpClient(private val client: HttpClient) : ZiplineHttpClient() {
    override suspend fun download(url: String, requestHeaders: List<Pair<String, String>>): ByteString = client
        .get(url) {
            headers {
                requestHeaders.forEach { (name, value) -> append(name, value) }
            }
        }
        .body<ByteArray>()
        .toByteString()
}

/**
 * キャッシュ済みの bundle を使わず、毎回配信先の manifest を取得する鮮度ポリシー。
 *
 * モジュールは SHA-256 でキャッシュと照合して再利用される。配信先へ届かない場合はロードが失敗し、キャッシュ済みの
 * bundle は実行されない。
 */
private object AlwaysStale : FreshnessChecker {
    override fun isFresh(manifest: ZiplineManifest, freshAtEpochMs: Long): Boolean = false
}

private fun Throwable.describe(): String = this::class.simpleName + message
    ?.takeIf(String::isNotBlank)
    ?.let { ": $it" }
    .orEmpty()
