package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.Zipline
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
import me.matsumo.fankt.fanbox.FanboxEmbeddedGuestBundle
import me.matsumo.fankt.fanbox.FanboxExceptionFactory
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.response.FanboxDiagnostics
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.FileHandle
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source

internal data class FanboxGuestDeliveryConfig(
    val manifestUrl: String,
    val trustedKeyName: String,
    val trustedEd25519PublicKey: ByteArray,
    val embeddedBundle: FanboxEmbeddedGuestBundle? = null,
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

/**
 * 同梱 bundle の読み出しに失敗した理由。
 *
 * manifest が無い場合と、manifest が参照するモジュールが欠けている場合を区別する。後者は同梱した
 * ディレクトリのレイアウトの取り違えかコピー漏れを示す。いずれも `FsEmbeddedFetcher` から見ると
 * 「ファイルが無い」で同じ症状になり、静かに既存経路へ退避するため、ここで区別して報告する。
 */
internal sealed interface EmbeddedBundleFailure {
    data object ManifestMissing : EmbeddedBundleFailure
    data class ModulesMissing(val fileNames: List<String>) : EmbeddedBundleFailure

    fun describe(): String = when (this) {
        ManifestMissing ->
            "embedded guest bundle has no $EMBEDDED_MANIFEST_FILE_NAME"
        is ModulesMissing ->
            "embedded guest bundle is missing ${fileNames.size} module(s) referenced by its " +
                "manifest: ${fileNames.joinToString()}"
    }
}

/**
 * 同梱 bundle の内容をメモリ上に読み込んだもの。
 *
 * 公開 API の読み出しは `suspend` だが [okio.FileSystem.source] は同期であり、guest は単一スレッド
 * で実行されるため橋渡しに `runBlocking` を使えない。そこで loader を呼ぶ前に必要なファイルを
 * すべて読み出しておく。読み出す対象は manifest を先に読めば決まる。
 */
internal class LoadedEmbeddedBundle private constructor(
    private val filesByName: Map<String, ByteString>,
) {
    val fileSystem: FileSystem get() = InMemoryReadOnlyFileSystem(filesByName)

    companion object {
        val DIRECTORY: Path = "/fanbox-guest-embedded".toPath()

        /** 同梱 bundle を読み出す。読み出せない場合は理由を [EmbeddedBundleFailure] で返す。 */
        suspend fun read(
            bundle: FanboxEmbeddedGuestBundle,
        ): Result<LoadedEmbeddedBundle> {
            val manifestBytes = bundle.read(EMBEDDED_MANIFEST_FILE_NAME)
                ?: return failure(EmbeddedBundleFailure.ManifestMissing)
            val manifest = ZiplineManifest.decodeJson(manifestBytes.decodeToString())

            val files = mutableMapOf(EMBEDDED_MANIFEST_FILE_NAME to manifestBytes.toByteString())
            val missing = mutableListOf<String>()
            for (module in manifest.modules.values) {
                val fileName = module.sha256.hex()
                val moduleBytes = bundle.read(fileName)
                if (moduleBytes == null) missing += fileName else files[fileName] = moduleBytes.toByteString()
            }

            if (missing.isNotEmpty()) return failure(EmbeddedBundleFailure.ModulesMissing(missing))

            return Result.success(LoadedEmbeddedBundle(files))
        }

        private fun failure(reason: EmbeddedBundleFailure): Result<LoadedEmbeddedBundle> =
            Result.failure(EmbeddedBundleReadException(reason))
    }
}

internal class EmbeddedBundleReadException(
    val failure: EmbeddedBundleFailure,
) : Exception(failure.describe())

/**
 * 読み出し済みの内容だけを返す [FileSystem]。
 *
 * embedded 経路が呼ぶのは `exists` と `read` で、それぞれ [metadataOrNull] と [source] に委譲される
 * （`FsEmbeddedFetcher`）。他の操作は呼ばれた時点でこの前提が崩れているため、黙って空を返さずに
 * 失敗させる。
 */
private class InMemoryReadOnlyFileSystem(
    private val filesByName: Map<String, ByteString>,
) : FileSystem() {
    private fun bytesOrNull(path: Path): ByteString? = filesByName[path.name]

    override fun metadataOrNull(path: Path): FileMetadata? {
        val bytes = bytesOrNull(path) ?: return null
        return FileMetadata(isRegularFile = true, size = bytes.size.toLong())
    }

    override fun source(file: Path): Source {
        val bytes = bytesOrNull(file) ?: throw FileNotFoundException("no such embedded file: $file")
        return Buffer().write(bytes)
    }

    override fun canonicalize(path: Path): Path = unsupported("canonicalize")
    override fun list(dir: Path): List<Path> = unsupported("list")
    override fun listOrNull(dir: Path): List<Path>? = unsupported("listOrNull")
    override fun openReadOnly(file: Path): FileHandle = unsupported("openReadOnly")
    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle =
        unsupported("openReadWrite")
    override fun sink(file: Path, mustCreate: Boolean): Sink = unsupported("sink")
    override fun appendingSink(file: Path, mustExist: Boolean): Sink = unsupported("appendingSink")
    override fun createDirectory(dir: Path, mustCreate: Boolean): Unit = unsupported("createDirectory")
    override fun atomicMove(source: Path, target: Path): Unit = unsupported("atomicMove")
    override fun delete(path: Path, mustExist: Boolean): Unit = unsupported("delete")
    override fun createSymlink(source: Path, target: Path): Unit = unsupported("createSymlink")

    private fun unsupported(operation: String): Nothing =
        error("the embedded guest bundle is read-only; $operation is not available")
}

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
        val descriptor = callGuest(guest) { service ->
            service.buildPostDetailRequest(postId.value)
        }.getOrElse { failure ->
            return disableAndFallback(guest, failure.describe(), direct)
        }

        val response = requestExecutor.execute(descriptor)
        val parsed = callGuest(guest) { service ->
            service.parsePostDetail(response.bodyText, response.statusCode)
        }.getOrElse { failure ->
            return disableAndFallback(guest, failure.describe(), direct)
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

    /**
     * Runs one bridge call on the guest's thread, reporting every failure but cancellation as a
     * value.
     *
     * The failure types are not enumerated here. A bridge call fails with `ZiplineException` when
     * the guest function itself threw, with `SerializationException` when this host cannot decode
     * what the guest returned, and with `ZiplineApiMismatchException` when the bundle's bridge API
     * differs from what this host expects — and the last of those extends `Exception` directly, so
     * it shares none of the others' supertypes. Listing them invites the next release to add a
     * fourth. The guest is the boundary at which untrusted input is interpreted, so no failure from
     * it is assumed to be predictable.
     *
     * `Error` stays uncaught: it signals a resource-exhausted or corrupted runtime, where the
     * direct path would fail for the same reason. Initialization in [guestOrNull] keeps catching
     * `Throwable` instead, because narrowing it there would turn a failure it currently retreats
     * from into one that reaches the caller.
     */
    private suspend fun <T> callGuest(
        guest: LoadedGuest,
        call: (FanboxGuestService) -> T,
    ): Result<T> = try {
        Result.success(withContext(guestDispatcher.value) { call(guest.service) })
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        Result.failure(failure)
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
                LoadedEmbeddedBundle.read(embedded).mapCatching { loaded ->
                    load(
                        loader = newLoader(EmbeddedOnlyZiplineHttpClient)
                            .withEmbedded(loaded.fileSystem, LoadedEmbeddedBundle.DIRECTORY),
                        manifestUrl = EMBEDDED_MANIFEST_SENTINEL_URL,
                        freshnessChecker = AlwaysFresh,
                    ).getOrThrow()
                }
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

// Zipline derives this from the application name (`getApplicationManifestFileName`), so it is not
// the `manifest.zipline.json` that the build produces.
internal const val EMBEDDED_MANIFEST_FILE_NAME = "$APPLICATION_NAME.manifest.zipline.json"
