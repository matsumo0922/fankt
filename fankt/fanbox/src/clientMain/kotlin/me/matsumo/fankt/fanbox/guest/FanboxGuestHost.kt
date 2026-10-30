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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.FanboxGuestCache
import me.matsumo.fankt.fanbox.FanboxGuestEvent
import me.matsumo.fankt.fanbox.FanboxHttpClientFactory
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpointId
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.response.FanboxDiagnostics
import me.matsumo.fankt.fanbox.response.FanboxFailureInterpreter
import me.matsumo.fankt.fanbox.transport.FanboxRawResponse
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.use
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** guest manifest の配信先・検証鍵・キャッシュの設定。[trustedKeys] は鍵名から Ed25519 公開鍵への対応。 */
internal class FanboxGuestDeliveryConfig(
    val manifestUrl: String,
    val trustedKeys: Map<String, ByteString>,
    val cache: FanboxGuestCache,
) {
    init {
        require(Url(manifestUrl).protocol == URLProtocol.HTTPS) { "Guest manifest URL must use HTTPS: $manifestUrl" }
        require(trustedKeys.isNotEmpty()) { "At least one trusted guest key is required" }
        require(trustedKeys.values.all { it.size == 32 }) { "Guest Ed25519 public keys must contain 32 bytes" }
    }

    /**
     * 信頼する鍵の組ごとのキャッシュのディレクトリ名。
     *
     * Zipline はキャッシュ済み manifest の署名を鮮度の判定より先に検証し、失敗するとロードを諦める。
     * 鍵の組が変わったときに古いキャッシュがロードを塞がないよう、組ごとに分ける。
     */
    val cacheDirectoryName: String = Buffer()
        .apply {
            trustedKeys.entries.sortedBy { it.key }.forEach { (name, key) ->
                // The length fixes where a name ends, so that two different sets never write the same bytes.
                writeInt(name.encodeToByteArray().size)
                writeUtf8(name)
                write(key)
            }
        }
        .sha256()
        .hex()
}

/**
 * Zipline guest を背景でロードし、FANBOX の request の組み立てと response の解析を guest で行う。
 *
 * 呼び出しはロードの完了を待つ。ロードに失敗した host の呼び出しは、すべて [FanboxException.GuestFailure] で失敗する。
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal class FanboxGuestHost(
    private val requestExecutor: FanboxRequestExecutor,
    private val diagnosticSink: FanboxDiagnosticSink,
    private val eventListener: (FanboxGuestEvent) -> Unit = {},
    load: suspend (CoroutineDispatcher) -> LoadedGuest,
) : AutoCloseable {
    // Zipline instances are thread-confined; limited parallelism does not guarantee one thread.
    private val dispatcher = newSingleThreadContext("FanboxZipline")

    // Read and written only on the dispatcher, so that close() releases the instance the load built.
    private var loaded: LoadedGuest? = null

    private val loading = CoroutineScope(dispatcher).async {
        val guest = try {
            load(dispatcher)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            report("load", failure.describe(), FanboxGuestEvent.LoadFailed(failure.errorType()))
            throw failure
        }

        loaded = guest
        emit(FanboxGuestEvent.Loaded(guest.version))
        guest.service
    }

    /** guest で request を組み立てて実行し、その response を guest で解析する。 */
    suspend fun <T> request(
        endpointId: FanboxEndpointId,
        build: FanboxGuestService.() -> RequestDescriptor,
        parse: FanboxGuestService.(FanboxRawResponse) -> GuestParseResult<T>,
    ): T {
        val endpoint = endpointId.value
        val descriptor = call(
            endpoint = endpoint,
            operation = "buildRequest",
            statusCode = null,
            block = build,
        )
        val response = requestExecutor.execute(descriptor)
        val result = call(
            endpoint = endpoint,
            operation = PARSE_RESPONSE,
            statusCode = response.statusCode,
        ) { parse(response) }

        return when (result) {
            is GuestParseResult.Success -> result.value

            is GuestParseResult.SchemaMismatch -> {
                report(PARSE_RESPONSE, "schema mismatch: ${result.diagnostic}", FanboxGuestEvent.SchemaMismatch(endpoint))
                throw FanboxFailureInterpreter.schemaMismatch(
                    statusCode = response.statusCode,
                    body = response.bodyText,
                    endpoint = endpoint,
                    cause = IllegalStateException(FanboxDiagnostics.sanitizeFragment(result.diagnostic)),
                )
            }

            is GuestParseResult.GuestFailure -> {
                report(PARSE_RESPONSE, result.diagnostic, FanboxGuestEvent.CallFailed(endpoint, PARSE_RESPONSE, "GuestFailure"))
                throw FanboxException.GuestFailure(
                    statusCode = response.statusCode,
                    endpoint = endpoint,
                    cause = IllegalStateException(FanboxDiagnostics.sanitizeFragment(result.diagnostic)),
                )
            }
        }
    }

    // Exception rather than a list of Zipline types: a bridge call can also fail with
    // SerializationException or ZiplineApiMismatchException, which share no supertype with the rest.
    private suspend fun <T> call(
        endpoint: String,
        operation: String,
        statusCode: Int?,
        block: FanboxGuestService.() -> T,
    ): T {
        val service = try {
            loading.await()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            throw FanboxException.GuestFailure(
                statusCode = statusCode,
                endpoint = endpoint,
                cause = failure,
            )
        }

        return try {
            withContext(dispatcher) { service.block() }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            report(operation, failure.describe(), FanboxGuestEvent.CallFailed(endpoint, operation, failure.errorType()))
            throw FanboxException.GuestFailure(
                statusCode = statusCode,
                endpoint = endpoint,
                cause = failure,
            )
        }
    }

    private fun report(
        operation: String,
        reason: String,
        event: FanboxGuestEvent,
    ) {
        diagnosticSink.report("Zipline guest $operation failed (${FanboxDiagnostics.sanitizeFragment(reason)})")
        emit(event)
    }

    // A listener failure is reported instead of propagated: thrown from the load coroutine, it would
    // replace the load result, and thrown from a call, it would hide the failure the call reports.
    private fun emit(event: FanboxGuestEvent) {
        try {
            eventListener(event)
        } catch (failure: Exception) {
            diagnosticSink.report("Guest event listener failed (${failure.errorType()})")
        }
    }

    override fun close() {
        try {
            runBlocking(dispatcher) {
                loading.cancelAndJoin()
                loaded?.zipline?.close()
                loaded = null
            }
        } finally {
            dispatcher.close()
        }
    }
}

/** ロード済みの guest service と、その Zipline・bundle のバージョン。 */
internal data class LoadedGuest(
    val service: FanboxGuestService,
    val zipline: Zipline? = null,
    val version: String? = null,
)

/** [config] の配信先から、[clientFactory] の HTTP client で guest をロードする関数を返す。 */
internal fun ziplineGuestLoader(
    config: FanboxGuestDeliveryConfig,
    clientFactory: FanboxHttpClientFactory,
): suspend (CoroutineDispatcher) -> LoadedGuest = { dispatcher ->
    createGuestHttpClient(clientFactory).use { client ->
        loadZiplineGuest(config, dispatcher, KtorZiplineHttpClient(client))
    }
}

/**
 * 署名を検証した配信 bundle から guest をロードする。[dispatcher] の上で呼び出す。
 *
 * [initializer] は Zipline の生成後、guest の main function の前に呼ばれる。
 */
internal suspend fun loadZiplineGuest(
    config: FanboxGuestDeliveryConfig,
    dispatcher: CoroutineDispatcher,
    httpClient: ZiplineHttpClient,
    initializer: suspend (Zipline) -> Unit = {},
): LoadedGuest {
    val manifestVerifier = Builder()
        .apply { config.trustedKeys.forEach { (name, key) -> addEd25519(name = name, trustedKey = key) } }
        .build()

    var created: Zipline? = null

    return config.cache.open(config.cacheDirectoryName, 10L * 1024 * 1024).use { cache ->
        try {
            val result = ZiplineLoader(dispatcher, manifestVerifier, httpClient)
                .withCache(cache)
                .loadOnce(
                    applicationName = "fanbox-guest",
                    freshnessChecker = AlwaysStale,
                    manifestUrl = config.manifestUrl,
                    initializer = { zipline ->
                        created = zipline
                        initializer(zipline)
                    },
                )

            when (result) {
                is LoadResult.Success -> LoadedGuest(
                    service = result.zipline.take(FANBOX_GUEST_SERVICE_NAME),
                    zipline = result.zipline,
                    version = result.manifest.version,
                )

                is LoadResult.Failure -> throw result.exception
            }
        } catch (failure: Throwable) {
            // Zipline closes an instance that fails while it is built, but not one that a
            // cancellation catches between being built and being returned.
            created?.close()
            throw failure
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

private fun Throwable.errorType(): String = this::class.simpleName ?: "Throwable"

private fun Throwable.describe(): String = this::class.simpleName + message
    ?.takeIf(String::isNotBlank)
    ?.let { ": $it" }
    .orEmpty()

private const val PARSE_RESPONSE = "parseResponse"
