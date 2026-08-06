package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.Zipline
import app.cash.zipline.ZiplineException
import app.cash.zipline.ZiplineManifest
import app.cash.zipline.loader.FreshnessChecker
import app.cash.zipline.loader.LoadResult
import app.cash.zipline.loader.ManifestVerifier
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.ZiplineLoader
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.FanboxExceptionFactory
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.response.FanboxDiagnostics
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path

internal data class FanboxGuestDeliveryConfig(
    val manifestUrl: String,
    val trustedKeyName: String,
    val trustedEd25519PublicKey: ByteArray,
    val embeddedBundle: EmbeddedGuestBundle? = null,
) {
    init {
        require(manifestUrl.isNotBlank()) { "Guest manifest URL must not be blank" }
        val parsedManifestUrl = Url(manifestUrl)
        require(
            parsedManifestUrl.protocol == URLProtocol.HTTPS ||
                parsedManifestUrl.protocol == URLProtocol.HTTP && parsedManifestUrl.host.isLoopbackHost(),
        ) {
            "Guest manifest URL must use HTTPS, except for a loopback development server"
        }
        require(parsedManifestUrl.user.isNullOrEmpty() && parsedManifestUrl.password.isNullOrEmpty()) {
            "Guest manifest URL must not contain credentials"
        }
        require(parsedManifestUrl.fragment.isEmpty()) { "Guest manifest URL must not contain a fragment" }
        require(trustedKeyName.isNotBlank()) { "Guest trusted key name must not be blank" }
        require(trustedEd25519PublicKey.size == ED25519_PUBLIC_KEY_SIZE) {
            "Guest Ed25519 public key must contain $ED25519_PUBLIC_KEY_SIZE bytes"
        }
    }

    private companion object {
        const val ED25519_PUBLIC_KEY_SIZE = 32
    }
}

internal data class EmbeddedGuestBundle(
    val fileSystem: FileSystem,
    val directory: Path,
)

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal class FanboxGuestHost(
    private val config: FanboxGuestDeliveryConfig,
    private val httpClientFactory: () -> HttpClient,
    private val diagnosticSink: FanboxDiagnosticSink,
    private val loadGuestOverride: (suspend (CoroutineDispatcher) -> LoadedGuest?)? = null,
) : AutoCloseable {
    private val stateMutex = Mutex()

    // Zipline instances are thread-confined; limited parallelism does not guarantee one thread.
    private val guestDispatcher = lazy {
        newSingleThreadContext("FanboxZipline")
    }
    private val httpClient = lazy(httpClientFactory)
    private var state: GuestState = GuestState.Uninitialized

    suspend fun getPostDetail(
        postId: FanboxPostId,
        requestExecutor: FanboxRequestExecutor,
        direct: suspend () -> FanboxPostDetail,
    ): FanboxPostDetail {
        val guest = guestOrNull() ?: return direct()
        val descriptor = try {
            withContext(guestDispatcher.value) {
                guest.service.buildPostDetailRequest(postId.value)
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: ZiplineException) {
            return disableAndFallback(guest, failure.message.orEmpty(), direct)
        }

        val response = requestExecutor.execute(descriptor)
        val parsed = try {
            withContext(guestDispatcher.value) {
                guest.service.parsePostDetail(response.bodyText, response.statusCode)
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: ZiplineException) {
            return disableAndFallback(guest, failure.message.orEmpty(), direct)
        }

        return when (parsed) {
            is GuestParseResult.Success -> parsed.postDetail
            is GuestParseResult.SchemaMismatch -> throw FanboxExceptionFactory.schemaMismatch(
                statusCode = response.statusCode,
                html = response.bodyText,
                endpoint = POST_DETAIL_ENDPOINT,
                cause = IllegalStateException(FanboxDiagnostics.sanitizeFragment(parsed.diagnostic)),
            )
            is GuestParseResult.GuestFailure -> disableAndFallback(guest, parsed.diagnostic, direct)
        }
    }

    override fun close() {
        if (!guestDispatcher.isInitialized()) return

        var closeFailure: Throwable? = null
        try {
            runBlocking(guestDispatcher.value) {
                stateMutex.withLock {
                    val active = state as? GuestState.Active
                    state = GuestState.Direct
                    active?.guest?.zipline?.close()
                }
            }
        } catch (failure: Throwable) {
            closeFailure = failure
        }
        try {
            if (httpClient.isInitialized()) httpClient.value.close()
        } catch (failure: Throwable) {
            val firstFailure = closeFailure
            if (firstFailure == null) closeFailure = failure else firstFailure.addSuppressed(failure)
        }
        try {
            guestDispatcher.value.close()
        } catch (failure: Throwable) {
            val firstFailure = closeFailure
            if (firstFailure == null) closeFailure = failure else firstFailure.addSuppressed(failure)
        }
        closeFailure?.let { throw it }
    }

    private suspend fun guestOrNull(): LoadedGuest? = stateMutex.withLock {
        when (val current = state) {
            is GuestState.Active -> current.guest
            GuestState.Direct -> null
            GuestState.Uninitialized -> {
                val loaded = try {
                    withContext(guestDispatcher.value) {
                        if (loadGuestOverride != null) {
                            loadGuestOverride.invoke(guestDispatcher.value)
                        } else {
                            ZiplineGuestLoader(
                                config = config,
                                dispatcher = guestDispatcher.value,
                                httpClient = KtorZiplineHttpClient(httpClient.value),
                                diagnosticSink = diagnosticSink,
                            ).load()
                        }
                    }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    diagnosticSink.report(
                        "Zipline guest initialization failed; using direct path (${failure.describe()})",
                    )
                    null
                }
                state = if (loaded != null) GuestState.Active(loaded) else GuestState.Direct
                loaded
            }
        }
    }

    private suspend fun disableAndFallback(
        failedGuest: LoadedGuest,
        diagnostic: String,
        direct: suspend () -> FanboxPostDetail,
    ): FanboxPostDetail {
        stateMutex.withLock {
            if ((state as? GuestState.Active)?.guest === failedGuest) {
                state = GuestState.Direct
                withContext(guestDispatcher.value) {
                    failedGuest.zipline?.close()
                }
            }
        }
        diagnosticSink.report(
            "Zipline guest execution failed; using direct path" +
                diagnostic.takeIf(String::isNotBlank)
                    ?.let(FanboxDiagnostics::sanitizeFragment)
                    ?.let { " ($it)" }
                    .orEmpty(),
        )
        return direct()
    }

    private sealed interface GuestState {
        data object Uninitialized : GuestState
        data class Active(val guest: LoadedGuest) : GuestState
        data object Direct : GuestState
    }
}

internal data class LoadedGuest(
    val service: FanboxGuestService,
    val zipline: Zipline? = null,
)

internal class ZiplineGuestLoader(
    private val config: FanboxGuestDeliveryConfig,
    private val dispatcher: CoroutineDispatcher,
    private val httpClient: ZiplineHttpClient,
    private val diagnosticSink: FanboxDiagnosticSink,
) {
    private val manifestVerifier = ManifestVerifier.Builder()
        .addEd25519(config.trustedKeyName, config.trustedEd25519PublicKey.toByteString())
        .build()

    suspend fun load(): LoadedGuest? = loadGuestWithFallback(
        remote = {
            load(
                loader = newLoader(),
                manifestUrl = config.manifestUrl,
                freshnessChecker = AlwaysStale,
            )
        },
        embedded = {
            val embedded = config.embeddedBundle
            if (embedded == null) {
                Result.failure(IllegalStateException("embedded guest bundle is not configured"))
            } else {
                load(
                    loader = newLoader(EmbeddedOnlyZiplineHttpClient)
                        .withEmbedded(embedded.fileSystem, embedded.directory),
                    manifestUrl = EMBEDDED_MANIFEST_SENTINEL_URL,
                    freshnessChecker = AlwaysFresh,
                )
            }
        },
        diagnosticSink = diagnosticSink,
    )

    private fun newLoader(client: ZiplineHttpClient = httpClient): ZiplineLoader = ZiplineLoader(
        dispatcher = dispatcher,
        manifestVerifier = manifestVerifier,
        httpClient = client,
    )

    private suspend fun load(
        loader: ZiplineLoader,
        manifestUrl: String,
        freshnessChecker: FreshnessChecker,
    ): Result<LoadedGuest> {
        val result = loader.loadOnce(
            applicationName = APPLICATION_NAME,
            freshnessChecker = freshnessChecker,
            manifestUrl = manifestUrl,
        )
        val mapped = result.mapSuccess { success ->
            LoadedGuest(
                service = success.zipline.take<FanboxGuestService>(FANBOX_GUEST_SERVICE_NAME),
                zipline = success.zipline,
            )
        }
        if (mapped.isFailure && result is LoadResult.Success) result.zipline.close()
        return mapped
    }
}

internal inline fun <T : Any> LoadResult.mapSuccess(
    transform: (LoadResult.Success) -> T,
): Result<T> = when (this) {
    is LoadResult.Success -> runCatching { transform(this) }
    is LoadResult.Failure -> Result.failure(exception)
}

internal suspend fun <T : Any> loadGuestWithFallback(
    remote: suspend () -> Result<T>,
    embedded: suspend () -> Result<T>,
    diagnosticSink: FanboxDiagnosticSink,
): T? {
    loadGuestAttempt("remote manifest", remote, diagnosticSink)?.let { return it }
    return loadGuestAttempt("embedded bundle", embedded, diagnosticSink)
}

private suspend fun <T : Any> loadGuestAttempt(
    stage: String,
    load: suspend () -> Result<T>,
    diagnosticSink: FanboxDiagnosticSink,
): T? = try {
    load().fold(
        onSuccess = { it },
        onFailure = { failure ->
            diagnosticSink.report("Zipline $stage failed; falling back (${failure.describe()})")
            null
        },
    )
} catch (failure: CancellationException) {
    throw failure
} catch (failure: Throwable) {
    diagnosticSink.report("Zipline $stage threw; falling back (${failure.describe()})")
    null
}

private class KtorZiplineHttpClient(
    private val client: HttpClient,
) : ZiplineHttpClient() {
    override suspend fun download(
        url: String,
        requestHeaders: List<Pair<String, String>>,
    ): ByteString = client.get(url) {
        headers {
            requestHeaders.forEach { (name, value) -> append(name, value) }
        }
    }.body<ByteArray>().toByteString()
}

private object AlwaysStale : FreshnessChecker {
    override fun isFresh(manifest: ZiplineManifest, freshAtEpochMs: Long): Boolean = false
}

private object AlwaysFresh : FreshnessChecker {
    override fun isFresh(manifest: ZiplineManifest, freshAtEpochMs: Long): Boolean = true
}

private object EmbeddedOnlyZiplineHttpClient : ZiplineHttpClient() {
    override suspend fun download(
        url: String,
        requestHeaders: List<Pair<String, String>>,
    ): ByteString = error("embedded guest bundle is incomplete")
}

private fun Throwable.describe(): String =
    this::class.simpleName + message?.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()

private fun String.isLoopbackHost(): Boolean =
    equals("localhost", ignoreCase = true) || this == "127.0.0.1" || this == "::1"

private const val APPLICATION_NAME = "fanbox-guest"
private const val POST_DETAIL_ENDPOINT = "post.info"
private const val EMBEDDED_MANIFEST_SENTINEL_URL = "https://embedded.invalid/manifest.zipline.json"
