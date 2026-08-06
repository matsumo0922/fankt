package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.ZiplineApiMismatchException
import app.cash.zipline.loader.LoadResult
import app.cash.zipline.loader.ZiplineHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.response.FanboxResponses
import me.matsumo.fankt.fanbox.transport.FanboxRawResponse
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor
import okio.ByteString
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FanboxGuestHostTest {

    @Test
    fun loadResultFailureFallsBackToEmbeddedBundle() = runBlocking {
        val expected = Any()
        val diagnostics = mutableListOf<String>()

        val actual = loadGuestWithFallback(
            remote = {
                LoadResult.Failure(IllegalStateException("network")).mapSuccess { error("success") }
            },
            embedded = { Result.success(expected) },
            diagnosticSink = FanboxDiagnosticSink(diagnostics::add),
        )

        assertSame(expected, actual)
        assertEquals(1, diagnostics.size)
        assertTrue("remote manifest failed" in diagnostics.single())
    }

    @Test
    fun thrownLocalFailureFallsBackToEmbeddedBundle() = runBlocking {
        val expected = Any()
        val diagnostics = mutableListOf<String>()

        val actual = loadGuestWithFallback(
            remote = { throw IllegalStateException("signature") },
            embedded = { Result.success(expected) },
            diagnosticSink = FanboxDiagnosticSink(diagnostics::add),
        )

        assertSame(expected, actual)
        assertEquals(1, diagnostics.size)
        assertTrue("remote manifest threw" in diagnostics.single())
    }

    @Test
    fun embeddedFailureFallsBackToDirectPath() = runBlocking {
        val diagnostics = mutableListOf<String>()

        val actual = loadGuestWithFallback<Any>(
            remote = { Result.failure(IllegalStateException("network")) },
            embedded = { throw IllegalStateException("embedded signature") },
            diagnosticSink = FanboxDiagnosticSink(diagnostics::add),
        )

        assertNull(actual)
        assertEquals(2, diagnostics.size)
        assertTrue("embedded bundle threw" in diagnostics.last())
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun embeddedSignatureThrowFallsBackToDirectPath() = runBlocking {
        val fileSystem = FileSystem.SYSTEM
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "fankt-zipline-${Random.nextLong()}"
        val diagnostics = mutableListOf<String>()
        val dispatcher = newSingleThreadContext("FanboxZiplineTest")
        fileSystem.createDirectories(directory)
        fileSystem.write(directory / "fanbox-guest.manifest.zipline.json") {
            writeUtf8(UNSIGNED_MANIFEST)
        }

        try {
            val loader = ZiplineGuestLoader(
                config = CONFIG.copy(
                    embeddedBundle = EmbeddedGuestBundle(fileSystem, directory),
                ),
                dispatcher = dispatcher,
                httpClient = object : ZiplineHttpClient() {
                    override suspend fun download(
                        url: String,
                        requestHeaders: List<Pair<String, String>>,
                    ): ByteString = error("network unavailable")
                },
                diagnosticSink = FanboxDiagnosticSink(diagnostics::add),
            )

            assertNull(loader.load())
            assertEquals(2, diagnostics.size)
            assertTrue("remote manifest failed" in diagnostics.first())
            assertTrue("embedded bundle threw" in diagnostics.last())
        } finally {
            dispatcher.close()
            fileSystem.deleteRecursively(directory)
        }
    }

    @Test
    fun schemaMismatchDoesNotUseDirectPath() = runBlocking {
        var directCalls = 0
        val host = hostWithService(
            parse = { _, _ -> GuestParseResult.SchemaMismatch("changed schema") },
        )
        try {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> {
                host.getPostDetail(POST_ID, successfulExecutor()) {
                    directCalls += 1
                    DIRECT_POST
                }
            }

            assertEquals(0, directCalls)
            assertEquals("post.info", failure.endpoint)
            assertEquals(200, failure.statusCode)
        } finally {
            host.close()
        }
    }

    @Test
    fun guestFailureMakesDirectFallbackSticky() = runBlocking {
        var parseCalls = 0
        var directCalls = 0
        val host = hostWithService(
            parse = { _, _ ->
                parseCalls += 1
                GuestParseResult.GuestFailure("broken guest")
            },
        )
        try {
            repeat(2) {
                val actual = host.getPostDetail(POST_ID, successfulExecutor()) {
                    directCalls += 1
                    DIRECT_POST
                }
                assertEquals(DIRECT_POST, actual)
            }

            assertEquals(1, parseCalls)
            assertEquals(2, directCalls)
        } finally {
            host.close()
        }
    }

    /**
     * A bridge call fails with a type that neither `CancellationException` nor `ZiplineException`
     * covers when this host cannot decode what the guest returned, or when the bundle's bridge API
     * differs from what it expects. Those arrive as `SerializationException` and
     * `ZiplineApiMismatchException`, and the latter extends `Exception` directly.
     */
    @Test
    fun aParseFailureOutsideZiplineExceptionFallsBackToDirectPath() = runBlocking {
        val diagnostics = mutableListOf<String>()
        var directCalls = 0
        val host = hostWithService(
            parse = { _, _ -> throw SerializationException("unexpected discriminator") },
            diagnosticSink = { message -> diagnostics += message },
        )
        try {
            repeat(2) {
                val actual = host.getPostDetail(POST_ID, successfulExecutor()) {
                    directCalls += 1
                    DIRECT_POST
                }
                assertEquals(DIRECT_POST, actual)
            }

            assertEquals(2, directCalls)
            assertTrue(diagnostics.any { "using direct path" in it })
        } finally {
            host.close()
        }
    }

    @Test
    fun aDescriptorFailureOutsideZiplineExceptionFallsBackToDirectPath() = runBlocking {
        var directCalls = 0
        val host = hostWithService(
            parse = { _, _ -> error("parse must not run once the descriptor call failed") },
            buildRequest = { throw ZiplineApiMismatchException("no such method") },
        )
        try {
            val actual = host.getPostDetail(POST_ID, successfulExecutor()) {
                directCalls += 1
                DIRECT_POST
            }

            assertEquals(DIRECT_POST, actual)
            assertEquals(1, directCalls)
        } finally {
            host.close()
        }
    }

    @Test
    fun cancellationIsNotTreatedAsAGuestFailure() = runBlocking {
        var directCalls = 0
        val host = hostWithService(
            parse = { _, _ -> throw CancellationException("caller went away") },
        )
        try {
            assertFailsWith<CancellationException> {
                host.getPostDetail(POST_ID, successfulExecutor()) {
                    directCalls += 1
                    DIRECT_POST
                }
            }

            assertEquals(0, directCalls)
        } finally {
            host.close()
        }
    }

    @Test
    fun constructionDoesNotInitializeGuest() {
        var loadCalls = 0
        val host = FanboxGuestHost(
            config = CONFIG,
            httpClientFactory = { error("HTTP client must not be created during construction") },
            diagnosticSink = FanboxDiagnosticSink.none,
            loadGuestOverride = {
                loadCalls += 1
                null
            },
        )

        host.close()

        assertEquals(0, loadCalls)
    }

    @Test
    fun initializationFailureIsNotRetried() = runBlocking {
        var loadCalls = 0
        var directCalls = 0
        val host = FanboxGuestHost(
            config = CONFIG,
            httpClientFactory = { error("HTTP client must not be created by the override") },
            diagnosticSink = FanboxDiagnosticSink.none,
            loadGuestOverride = {
                loadCalls += 1
                null
            },
        )
        try {
            repeat(2) {
                host.getPostDetail(POST_ID, successfulExecutor()) {
                    directCalls += 1
                    DIRECT_POST
                }
            }

            assertEquals(1, loadCalls)
            assertEquals(2, directCalls)
        } finally {
            host.close()
        }
    }

    @Test
    fun concurrentCallersShareOneInitialization() = runBlocking {
        var loadCalls = 0
        val host = FanboxGuestHost(
            config = CONFIG,
            httpClientFactory = { error("HTTP client must not be created by the override") },
            diagnosticSink = FanboxDiagnosticSink.none,
            loadGuestOverride = {
                loadCalls += 1
                // Loading is slow enough in practice that a second caller arrives during it; the
                // yield reproduces that without a timing assumption.
                yield()
                LoadedGuest(service = StubGuestService { _, _ -> GuestParseResult.Success(GUEST_POST) })
            },
        )
        try {
            val results = List(4) {
                async { host.getPostDetail(POST_ID, successfulExecutor()) { DIRECT_POST } }
            }.awaitAll()

            // A second engine would mean two QuickJS instances on one dispatcher, which Zipline's
            // thread-confinement does not allow.
            assertEquals(1, loadCalls)
            assertTrue(results.all { it == GUEST_POST })
        } finally {
            host.close()
        }
    }

    @Test
    fun concurrentCallersDisableTheGuestOnce() = runBlocking {
        var loadCalls = 0
        var directCalls = 0
        val host = FanboxGuestHost(
            config = CONFIG,
            httpClientFactory = { error("HTTP client must not be created by the override") },
            diagnosticSink = FanboxDiagnosticSink.none,
            loadGuestOverride = {
                loadCalls += 1
                LoadedGuest(service = StubGuestService { _, _ -> GuestParseResult.GuestFailure("broken") })
            },
        )
        try {
            val results = List(4) {
                async {
                    host.getPostDetail(POST_ID, successfulExecutor()) {
                        directCalls += 1
                        DIRECT_POST
                    }
                }
            }.awaitAll()

            // Every caller falls back, and none of them reloads the guest that just failed.
            assertEquals(1, loadCalls)
            assertEquals(4, directCalls)
            assertTrue(results.all { it == DIRECT_POST })
        } finally {
            host.close()
        }
    }

    @Test
    fun closingAnInitializedHostStopsUsingTheGuest() = runBlocking {
        var loadCalls = 0
        val host = FanboxGuestHost(
            config = CONFIG,
            httpClientFactory = { error("HTTP client must not be created by the override") },
            diagnosticSink = FanboxDiagnosticSink.none,
            loadGuestOverride = {
                loadCalls += 1
                LoadedGuest(service = StubGuestService { _, _ -> GuestParseResult.Success(GUEST_POST) })
            },
        )
        assertEquals(GUEST_POST, host.getPostDetail(POST_ID, successfulExecutor()) { DIRECT_POST })
        assertEquals(1, loadCalls)

        host.close()

        // Closing releases the engine, so the guest is gone rather than reloaded on the next call.
        assertEquals(DIRECT_POST, host.getPostDetail(POST_ID, successfulExecutor()) { DIRECT_POST })
        assertEquals(1, loadCalls)
    }

    @Test
    fun sealedPostBodyRoundTripsInGuestPayload() {
        val post = FanboxResponses.postDetail(FanboxPostJsonFixtures.postInfoArticleEmbeds, 200)
        val encoded = Json.encodeToString<GuestParseResult>(GuestParseResult.Success(post))
        val decoded = Json.decodeFromString<GuestParseResult>(encoded)

        assertEquals(post, assertIs<GuestParseResult.Success>(decoded).postDetail)
    }

    private class StubGuestService(
        private val parse: (String, Int) -> GuestParseResult,
    ) : FanboxGuestService {
        override fun buildPostDetailRequest(postId: String): RequestDescriptor =
            FanboxEndpoints.postDetail(FanboxPostId(postId))

        override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult =
            parse(body, statusCode)
    }

    private fun hostWithService(
        parse: (String, Int) -> GuestParseResult,
        buildRequest: (String) -> RequestDescriptor = { postId ->
            FanboxEndpoints.postDetail(FanboxPostId(postId))
        },
        diagnosticSink: FanboxDiagnosticSink = FanboxDiagnosticSink.none,
    ): FanboxGuestHost = FanboxGuestHost(
        config = CONFIG,
        httpClientFactory = { error("HTTP client must not be created by the override") },
        diagnosticSink = diagnosticSink,
        loadGuestOverride = {
            LoadedGuest(
                service = object : FanboxGuestService {
                    override fun buildPostDetailRequest(postId: String): RequestDescriptor =
                        buildRequest(postId)

                    override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult =
                        parse(body, statusCode)
                },
            )
        },
    )

    private fun successfulExecutor(): FanboxRequestExecutor = FanboxRequestExecutor {
        FanboxRawResponse(
            statusCode = 200,
            headers = emptyMap(),
            bodyText = FanboxPostJsonFixtures.postInfoTextHybrid,
        )
    }

    private companion object {
        val POST_ID = FanboxPostId("10000001")
        val CONFIG = FanboxGuestDeliveryConfig(
            manifestUrl = "https://example.invalid/manifest.zipline.json",
            trustedKeyName = "test",
            trustedEd25519PublicKey = ByteArray(32),
        )
        val DIRECT_POST = FanboxResponses.postDetail(FanboxPostJsonFixtures.postInfoTextHybrid, 200)

        // A different post than DIRECT_POST, so a test can tell which path produced its result.
        val GUEST_POST = FanboxResponses.postDetail(FanboxPostJsonFixtures.postInfoArticleA, 200)
        const val UNSIGNED_MANIFEST =
            "{\"unsigned\":{\"signatures\":{},\"freshAtEpochMs\":null,\"baseUrl\":null}," +
                "\"modules\":{},\"mainModuleId\":\"./main.js\",\"mainFunction\":null," +
                "\"version\":null,\"metadata\":{}}"
    }
}
