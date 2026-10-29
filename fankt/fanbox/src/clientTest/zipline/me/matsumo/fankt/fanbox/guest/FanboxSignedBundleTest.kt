// ponytail: Zipline has no public multiplatform key-generation API. Keep its internal seed
// derivation confined to tests until upstream exposes one; production uses only public verification.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.ZiplineManifest
import app.cash.zipline.loader.ManifestSigner.Builder
import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.internal.tink.subtle.KeyPair
import app.cash.zipline.loader.internal.tink.subtle.newKeyPairFromSeed
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.FanboxGuestCache
import me.matsumo.fankt.fanbox.FanboxHttpClientFactory
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class FanboxSignedBundleTest {

    @Test
    fun validSignatureLoadsGuestBundle(): Unit = runBlocking {
        withBundleFixture {
            val result = loadGuest(signedBundle(FIXED_BUNDLE_DIRECTORY)).parseAndClose()

            assertIs<GuestParseResult.Success>(result)
        }
    }

    @Test
    fun tamperedSignatureIsRefused(): Unit = runBlocking {
        withBundleFixture {
            val remote = signedBundle(FIXED_BUNDLE_DIRECTORY, tamperSignature = true)

            assertFailsWith<IllegalStateException> { loadGuest(remote) }
        }
    }

    @Test
    fun unknownSigningKeyIsRefused(): Unit = runBlocking {
        withBundleFixture {
            val remote = signedBundle(FIXED_BUNDLE_DIRECTORY, signingKeyName = "unknown")

            assertFailsWith<IllegalStateException> { loadGuest(remote) }
        }
    }

    @Test
    fun manifestReplacementChangesResponseInterpretation(): Unit = runBlocking {
        withBundleFixture {
            val legacyResult = loadGuest(signedBundle(LEGACY_BUNDLE_DIRECTORY)).parseAndClose()
            val fixedResult = loadGuest(signedBundle(FIXED_BUNDLE_DIRECTORY)).parseAndClose()

            assertIs<GuestParseResult.SchemaMismatch>(legacyResult)
            assertIs<GuestParseResult.Success>(fixedResult)
        }
    }

    @Test
    fun cachedModulesAreNotDownloadedAgain(): Unit = runBlocking {
        withBundleFixture {
            val remote = signedBundle(FIXED_BUNDLE_DIRECTORY)
            loadGuest(remote).parseAndClose()
            remote.httpClient.downloadedUrls.clear()

            loadGuest(remote).parseAndClose()

            assertEquals(listOf(MANIFEST_URL), remote.httpClient.downloadedUrls)
        }
    }

    @Test
    fun cachedBundleDoesNotRunWhenTheManifestIsUnreachable(): Unit = runBlocking {
        withBundleFixture {
            loadGuest(signedBundle(FIXED_BUNDLE_DIRECTORY)).parseAndClose()

            assertFailsWith<IllegalStateException> { loadGuest(httpClient = UnreachableZiplineHttpClient) }
        }
    }

    @Test
    fun aCacheWrittenUnderAnotherKeyDoesNotBlockLoading(): Unit = runBlocking {
        withBundleFixture {
            loadGuest(signedBundle(FIXED_BUNDLE_DIRECTORY)).parseAndClose()
            val rotatedKey = newKeyPairFromSeed(Random.nextBytes(32).toByteString())

            val result = loadGuest(
                remote = signedBundle(FIXED_BUNDLE_DIRECTORY, keyPair = rotatedKey),
                keyPair = rotatedKey,
            ).parseAndClose()

            assertIs<GuestParseResult.Success>(result)
        }
    }

    @Test
    fun aSlowRequestEndsTheLoad(): Unit = runBlocking {
        withBundleFixture {
            val slowClientFactory = FanboxHttpClientFactory { block ->
                HttpClient(
                    engine = MockEngine {
                        delay(10.seconds)
                        respond("")
                    },
                    block = block,
                )
            }

            createGuestHttpClient(slowClientFactory, requestTimeout = 100.milliseconds).use { client ->
                assertFailsWith<HttpRequestTimeoutException> { loadGuest(httpClient = KtorZiplineHttpClient(client)) }
            }
        }
    }

    private suspend fun <T> withBundleFixture(block: suspend BundleFixture.() -> T): T = BundleFixture().use { it.block() }

    private class BundleFixture : AutoCloseable {
        val dispatcher = newSingleThreadContext("FanboxSignedBundleTest")
        private val fileSystem = FileSystem.SYSTEM
        private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "fankt-signed-bundle-${Random.nextLong()}"
        private val keyPair = newKeyPairFromSeed(Random.nextBytes(32).toByteString())
        private val cache = FanboxGuestCache { directoryName, maxSizeInBytes ->
            ZiplineCache(fileSystem, root / directoryName, maxSizeInBytes)
        }

        fun signedBundle(
            sourceDirectoryName: String,
            signingKeyName: String = TRUSTED_KEY_NAME,
            tamperSignature: Boolean = false,
            keyPair: KeyPair = this.keyPair,
        ): SignedBundle {
            val sourceDirectory = findBundleDirectory(sourceDirectoryName)
            val unsignedManifest = ZiplineManifest.decodeJson(
                fileSystem.read(sourceDirectory / "manifest.zipline.json") { readUtf8() },
            )
            var manifest = Builder()
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

            val modulesByFileName = manifest.modules.values.associate { module ->
                module.url.substringAfterLast('/') to fileSystem.read(sourceDirectory / module.url) { readByteString() }
            }
            return SignedBundle(BundleZiplineHttpClient(manifest.encodeJson(), modulesByFileName))
        }

        suspend fun loadGuest(
            remote: SignedBundle? = null,
            keyPair: KeyPair = this.keyPair,
            httpClient: ZiplineHttpClient = checkNotNull(remote).httpClient,
        ): LoadedGuest = withContext(dispatcher) {
            val config = FanboxGuestDeliveryConfig(
                manifestUrl = MANIFEST_URL,
                trustedKeyName = TRUSTED_KEY_NAME,
                trustedEd25519PublicKey = keyPair.publicKey,
                cache = cache,
            )

            loadZiplineGuest(config, dispatcher, httpClient)
        }

        suspend fun LoadedGuest.parseAndClose(): GuestParseResult = withContext(dispatcher) {
            try {
                service.parsePostDetail(RESPONSE_BODY, 200)
            } finally {
                zipline?.close()
            }
        }

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

    private data class SignedBundle(val httpClient: BundleZiplineHttpClient)

    private class BundleZiplineHttpClient(private val manifestJson: String, private val modulesByFileName: Map<String, ByteString>) : ZiplineHttpClient() {
        val downloadedUrls = mutableListOf<String>()

        override suspend fun download(url: String, requestHeaders: List<Pair<String, String>>): ByteString {
            downloadedUrls += url

            if (url == MANIFEST_URL) return manifestJson.encodeToByteArray().toByteString()

            return modulesByFileName[url.substringAfterLast('/')] ?: error("Unexpected bundle URL: $url")
        }
    }

    private object UnreachableZiplineHttpClient : ZiplineHttpClient() {
        override suspend fun download(url: String, requestHeaders: List<Pair<String, String>>): ByteString = error("network unavailable")
    }

    private companion object {
        const val FIXED_BUNDLE_DIRECTORY = "GuestProduction"
        const val LEGACY_BUNDLE_DIRECTORY = "LegacyguestProduction"
        const val TRUSTED_KEY_NAME = "test-key"
        const val MANIFEST_URL = "https://example.invalid/manifest.zipline.json"
        const val RESPONSE_BODY =
            """{"body":{"post":{"body":{"text":"OTA fixture"},"commentCount":0,"creatorId":"fixture-creator","excerpt":"Fixture excerpt","feeRequired":0,"hasAdultContent":false,"id":"10000001","imageForShare":"https://example.invalid/share.png","isLiked":false,"isRestricted":false,"likeCount":0,"nextPost":null,"prevPost":null,"publishedDatetime":"2026-01-01T00:00:00Z","tags":[],"title":"Fixture title","type":"text","updatedDatetime":"2026-01-01T00:00:00Z","coverImageUrl":null,"user":null}}}"""
    }
}
