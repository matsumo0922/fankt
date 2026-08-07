// ponytail: Zipline has no public multiplatform key-generation API. Keep its internal seed
// derivation confined to tests until upstream exposes one; production uses only public verification.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.ZiplineManifest
import app.cash.zipline.loader.ManifestSigner
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.internal.tink.subtle.newKeyPairFromSeed
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.FanboxEmbeddedGuestBundle
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.transport.FanboxRawResponse
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class FanboxSignedBundleTest {

    @Test
    fun validSignatureLoadsGuestBundle(): Unit = runBlocking {
        withBundleFixture {
            val remote = signedBundle(FIXED_BUNDLE_DIRECTORY)
            val loaded = loadGuest(remote = remote)

            try {
                assertIs<GuestParseResult.Success>(
                    parse(
                        assertNotNull(loaded),
                        RESPONSE_BODY,
                    ),
                )
            } finally {
                loaded?.let { closeGuest(it) }
            }
        }
    }

    @Test
    fun tamperedSignatureFallsBackToEmbeddedBundle(): Unit = runBlocking {
        withBundleFixture {
            val remote = signedBundle(LEGACY_BUNDLE_DIRECTORY, tamperSignature = true)
            val embedded = signedBundle(FIXED_BUNDLE_DIRECTORY)
            val diagnostics = mutableListOf<String>()
            val loaded = loadGuest(remote, embedded, diagnostics)

            try {
                assertIs<GuestParseResult.Success>(
                    parse(
                        assertNotNull(loaded),
                        RESPONSE_BODY,
                    ),
                )
                assertTrue(diagnostics.any { "remote manifest failed" in it })
            } finally {
                loaded?.let { closeGuest(it) }
            }
        }
    }

    @Test
    fun unknownSigningKeyFallsBackToEmbeddedBundle(): Unit = runBlocking {
        withBundleFixture {
            val remote = signedBundle(LEGACY_BUNDLE_DIRECTORY, signingKeyName = "unknown")
            val embedded = signedBundle(FIXED_BUNDLE_DIRECTORY)
            val diagnostics = mutableListOf<String>()
            val loaded = loadGuest(remote, embedded, diagnostics)

            try {
                assertIs<GuestParseResult.Success>(
                    parse(
                        assertNotNull(loaded),
                        RESPONSE_BODY,
                    ),
                )
                assertTrue(diagnostics.any { "remote manifest failed" in it })
            } finally {
                loaded?.let { closeGuest(it) }
            }
        }
    }

    @Test
    fun embeddedSignatureFailureUsesDirectPath(): Unit = runBlocking {
        withBundleFixture {
            val embedded = signedBundle(FIXED_BUNDLE_DIRECTORY, tamperSignature = true)
            var directCalls = 0
            val host = FanboxGuestHost(
                config = config(embedded),
                httpClientFactory = {
                    HttpClient(MockEngine { error("network unavailable") })
                },
                diagnosticSink = FanboxDiagnosticSink.none,
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
    }

    @Test
    fun unreachableManifestUsesEmbeddedBundle(): Unit = runBlocking {
        withBundleFixture {
            val embedded = signedBundle(FIXED_BUNDLE_DIRECTORY)
            val diagnostics = mutableListOf<String>()
            val loaded = loadGuest(remote = null, embedded = embedded, diagnostics = diagnostics)

            try {
                assertIs<GuestParseResult.Success>(
                    parse(
                        assertNotNull(loaded),
                        RESPONSE_BODY,
                    ),
                )
                assertTrue(diagnostics.any { "remote manifest failed" in it })
            } finally {
                loaded?.let { closeGuest(it) }
            }
        }
    }

    @Test
    fun manifestReplacementChangesResponseInterpretation(): Unit = runBlocking {
        withBundleFixture {
            val legacy = signedBundle(LEGACY_BUNDLE_DIRECTORY)
            val fixed = signedBundle(FIXED_BUNDLE_DIRECTORY)

            val legacyGuest = assertNotNull(loadGuest(remote = legacy))
            val legacyResult = try {
                parse(legacyGuest, RESPONSE_BODY)
            } finally {
                closeGuest(legacyGuest)
            }
            val fixedGuest = assertNotNull(loadGuest(remote = fixed))
            val fixedResult = try {
                parse(fixedGuest, RESPONSE_BODY)
            } finally {
                closeGuest(fixedGuest)
            }

            assertIs<GuestParseResult.SchemaMismatch>(legacyResult)
            assertIs<GuestParseResult.Success>(fixedResult)
        }
    }

    private suspend fun BundleFixture.loadGuest(
        remote: SignedBundle?,
        embedded: SignedBundle? = null,
        diagnostics: MutableList<String> = mutableListOf(),
    ): LoadedGuest? = withContext(dispatcher) {
        ZiplineGuestLoader(
            config = config(embedded),
            dispatcher = dispatcher,
            httpClient = remote?.httpClient ?: UnreachableZiplineHttpClient,
            diagnosticSink = FanboxDiagnosticSink(diagnostics::add),
        ).load() ?: error(diagnostics.joinToString("\n"))
    }

    private fun successfulExecutor(): FanboxRequestExecutor = FanboxRequestExecutor {
        FanboxRawResponse(
            statusCode = 200,
            headers = emptyMap(),
            bodyText = RESPONSE_BODY,
        )
    }

    private suspend fun <T> withBundleFixture(block: suspend BundleFixture.() -> T): T {
        val fixture = BundleFixture()
        return try {
            fixture.block()
        } finally {
            fixture.close()
        }
    }

    private class BundleFixture : AutoCloseable {
        val dispatcher = newSingleThreadContext("FanboxSignedBundleTest")
        private val fileSystem = FileSystem.SYSTEM
        private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "fankt-signed-bundle-${Random.nextLong()}"
        private val keyPair = newKeyPairFromSeed(Random.nextBytes(32).toByteString())

        init {
            fileSystem.createDirectories(root)
        }

        fun signedBundle(
            sourceDirectoryName: String,
            signingKeyName: String = TRUSTED_KEY_NAME,
            tamperSignature: Boolean = false,
        ): SignedBundle {
            val sourceDirectory = findBundleDirectory(sourceDirectoryName)
            val unsignedManifest = ZiplineManifest.decodeJson(
                fileSystem.read(sourceDirectory / "manifest.zipline.json") { readUtf8() },
            )
            var manifest = ManifestSigner.Builder()
                .addEd25519(signingKeyName, keyPair.privateKey)
                .build()
                .sign(unsignedManifest.copy(freshAtEpochMs = 0L))
            if (tamperSignature) {
                manifest = manifest.copy(
                    signatures = manifest.signatures.mapValues { (_, signature) ->
                        (if (signature.first() == 'A') 'B' else 'A') + signature.drop(1)
                    },
                )
            }

            // The embedded stage reads through the public API, so the bundle is held by file name
            // rather than written to disk. The names follow what the loader looks for: the manifest
            // carries the application name, and each module is named by its SHA-256.
            val embeddedFiles = mutableMapOf(
                EMBEDDED_MANIFEST_FILE_NAME to manifest.encodeJson().encodeToByteArray(),
            )
            val modulesByFileName = manifest.modules.values.associate { module ->
                val bytes = fileSystem.read(sourceDirectory / module.url) { readByteString() }
                embeddedFiles[module.sha256.hex()] = bytes.toByteArray()
                module.url.substringAfterLast('/') to bytes
            }
            return SignedBundle(
                embedded = FanboxEmbeddedGuestBundle(embeddedFiles::get),
                httpClient = BundleZiplineHttpClient(manifest.encodeJson(), modulesByFileName),
            )
        }

        private suspend fun parse(loaded: LoadedGuest, body: String): GuestParseResult = withContext(dispatcher) {
            loaded.service.parsePostDetail(body, 200)
        }

        suspend fun closeGuest(loaded: LoadedGuest) = withContext(dispatcher) {
            loaded.zipline?.close()
        }

        fun config(embedded: SignedBundle? = null): FanboxGuestDeliveryConfig = FanboxGuestDeliveryConfig(
            manifestUrl = MANIFEST_URL,
            trustedKeyName = TRUSTED_KEY_NAME,
            trustedEd25519PublicKey = keyPair.publicKey.toByteArray(),
            embeddedBundle = embedded?.embedded,
        )

        override fun close() {
            fileSystem.deleteRecursively(root)
            dispatcher.close()
        }

        private fun findBundleDirectory(name: String): Path {
            val candidates = listOf(
                "build/zipline/$name".toPath(),
                "fankt/fanbox/build/zipline/$name".toPath(),
            )
            return candidates.firstOrNull { fileSystem.metadataOrNull(it / "manifest.zipline.json") != null }
                ?: error("Zipline test bundle not found: $name")
        }
    }

    private data class SignedBundle(
        val embedded: FanboxEmbeddedGuestBundle,
        val httpClient: ZiplineHttpClient,
    )

    private class BundleZiplineHttpClient(
        private val manifestJson: String,
        private val modulesByFileName: Map<String, ByteString>,
    ) : ZiplineHttpClient() {
        override suspend fun download(
            url: String,
            requestHeaders: List<Pair<String, String>>,
        ): ByteString = if (url == MANIFEST_URL) {
            manifestJson.encodeToByteArray().toByteString()
        } else {
            modulesByFileName[url.substringAfterLast('/')]
                ?: error("Unexpected bundle URL: $url")
        }
    }

    private object UnreachableZiplineHttpClient : ZiplineHttpClient() {
        override suspend fun download(
            url: String,
            requestHeaders: List<Pair<String, String>>,
        ): ByteString = error("network unavailable")
    }

    private companion object {
        const val FIXED_BUNDLE_DIRECTORY = "GuestProduction"
        const val LEGACY_BUNDLE_DIRECTORY = "LegacyguestProduction"
        const val TRUSTED_KEY_NAME = "test-key"
        const val MANIFEST_URL = "https://example.invalid/manifest.zipline.json"
        const val RESPONSE_BODY =
            """{"body":{"post":{"body":{"text":"OTA fixture"},"commentCount":0,"creatorId":"fixture-creator","excerpt":"Fixture excerpt","feeRequired":0,"hasAdultContent":false,"id":"10000001","imageForShare":"https://example.invalid/share.png","isLiked":false,"isRestricted":false,"likeCount":0,"nextPost":null,"prevPost":null,"publishedDatetime":"2026-01-01T00:00:00Z","tags":[],"title":"Fixture title","type":"text","updatedDatetime":"2026-01-01T00:00:00Z","coverImageUrl":null,"user":null}}}"""
        val POST_ID = FanboxPostId("10000001")
        val DIRECT_POST = me.matsumo.fankt.fanbox.response.FanboxResponses.postDetail(
            RESPONSE_BODY,
            200,
        )
    }
}
