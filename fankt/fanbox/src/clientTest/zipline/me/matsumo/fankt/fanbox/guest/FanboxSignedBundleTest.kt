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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.matsumo.fankt.fanbox.FanboxGuestCache
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
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

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class FanboxSignedBundleTest {

    @Test
    fun everyOperationCrossesTheBridge(): Unit = runBlocking {
        withBundleFixture {
            val loaded = loadGuest(signedBundle(FIXED_BUNDLE_DIRECTORY))
            val native = RealFanboxGuestService()

            withContext(dispatcher) {
                try {
                    val bridged = loaded.service

                    assertEquals(GuestOperationCases.buildRequests(native), GuestOperationCases.buildRequests(bridged))
                    assertEquals(GuestOperationCases.parseResponses(native), GuestOperationCases.parseResponses(bridged))
                    // A rejection's diagnostic carries the engine's own message, so only its kind is compared.
                    assertEquals(
                        GuestOperationCases.parseRejectedResponses(native).map { result -> result::class },
                        GuestOperationCases.parseRejectedResponses(bridged).map { result -> result::class },
                    )
                } finally {
                    loaded.zipline?.close()
                }
            }
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
    fun aManifestSignedByAnyTrustedKeyIsAccepted(): Unit = runBlocking {
        withBundleFixture {
            val recoveryKey = newKeyPairFromSeed(Random.nextBytes(32).toByteString())
            val config = config(mapOf(TRUSTED_KEY_NAME to keyPair.publicKey, RECOVERY_KEY_NAME to recoveryKey.publicKey))
            val signedByPrimary = signedBundle(FIXED_BUNDLE_DIRECTORY)
            val signedByRecovery = signedBundle(FIXED_BUNDLE_DIRECTORY, signingKeyName = RECOVERY_KEY_NAME, keyPair = recoveryKey)

            assertIs<GuestParseResult.Success<*>>(loadGuest(signedByPrimary, config = config).parseAndClose())
            assertIs<GuestParseResult.Success<*>>(loadGuest(signedByRecovery, config = config).parseAndClose())
        }
    }

    @Test
    fun theCachedBundleLoadsWhenTheManifestIsUnreachable(): Unit = runBlocking {
        withBundleFixture {
            loadGuest(signedBundle(FIXED_BUNDLE_DIRECTORY, version = "cached")).parseAndClose()

            val loaded = loadGuest(httpClient = UnreachableZiplineHttpClient)

            assertIs<GuestParseResult.Success<*>>(loaded.parseAndClose())
            assertEquals("cached", loaded.version)
        }
    }

    @Test
    fun theCachedBundleLoadsWhenTheDeliveredManifestIsRefused(): Unit = runBlocking {
        withBundleFixture {
            loadGuest(signedBundle(FIXED_BUNDLE_DIRECTORY, version = "cached")).parseAndClose()

            val loaded = loadGuest(signedBundle(FIXED_BUNDLE_DIRECTORY, tamperSignature = true))

            assertIs<GuestParseResult.Success<*>>(loaded.parseAndClose())
            assertEquals("cached", loaded.version)
        }
    }

    @Test
    fun nothingLoadsWithoutTheManifestAndACachedBundle(): Unit = runBlocking {
        withBundleFixture {
            assertFailsWith<IllegalStateException> { loadGuest(httpClient = UnreachableZiplineHttpClient) }
        }
    }

    @Test
    fun aCachedBundleMissingAModuleDoesNotLoad(): Unit = runBlocking {
        withBundleFixture {
            val remote = signedBundle(FIXED_BUNDLE_DIRECTORY)
            loadGuest(remote).parseAndClose()
            cachedModuleFiles(remote).forEach(fileSystem::delete)

            assertCachedBundleDoesNotLoad(remote)
        }
    }

    @Test
    fun aTamperedCachedModuleDoesNotLoad(): Unit = runBlocking {
        withBundleFixture {
            val remote = signedBundle(FIXED_BUNDLE_DIRECTORY)
            loadGuest(remote).parseAndClose()
            cachedModuleFiles(remote).forEach { file -> fileSystem.write(file) { writeUtf8("tampered") } }

            assertCachedBundleDoesNotLoad(remote)
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

            assertIs<GuestParseResult.Success<*>>(result)
        }
    }

    private suspend fun <T> withBundleFixture(block: suspend BundleFixture.() -> T): T = BundleFixture().use { it.block() }

    private class BundleFixture : AutoCloseable {
        val dispatcher = newSingleThreadContext("FanboxSignedBundleTest")
        val fileSystem = FileSystem.SYSTEM
        private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "fankt-signed-bundle-${Random.nextLong()}"
        val keyPair = newKeyPairFromSeed(Random.nextBytes(32).toByteString())
        private val cache = FanboxGuestCache { directoryName, maxSizeInBytes ->
            ZiplineCache(fileSystem, root / directoryName, maxSizeInBytes)
        }

        fun signedBundle(
            sourceDirectoryName: String,
            signingKeyName: String = TRUSTED_KEY_NAME,
            tamperSignature: Boolean = false,
            keyPair: KeyPair = this.keyPair,
            version: String? = null,
        ): SignedBundle {
            val sourceDirectory = findBundleDirectory(sourceDirectoryName)
            val unsignedManifest = ZiplineManifest.decodeJson(
                fileSystem.read(sourceDirectory / "manifest.zipline.json") { readUtf8() },
            )
            var manifest = Builder()
                .addEd25519(signingKeyName, keyPair.privateKey)
                .build()
                .sign(unsignedManifest.copy(freshAtEpochMs = 0L, version = version))
            if (tamperSignature) {
                manifest = manifest.copy(
                    // Hex decoding ignores case, so the replacement digit must differ in value.
                    signatures = manifest.signatures.mapValues { (_, signature) ->
                        (if (signature.first() == '0') '1' else '0') + signature.drop(1)
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
            config: FanboxGuestDeliveryConfig = config(keyPair),
        ): LoadedGuest = withContext(dispatcher) {
            loadZiplineGuest(config, dispatcher, httpClient)
        }

        fun config(keyPair: KeyPair = this.keyPair) = config(mapOf(TRUSTED_KEY_NAME to keyPair.publicKey))

        fun config(trustedKeys: Map<String, ByteString>) = FanboxGuestDeliveryConfig(
            manifestUrl = MANIFEST_URL,
            trustedKeys = trustedKeys,
            cache = cache,
        )

        suspend fun LoadedGuest.parseAndClose(): GuestParseResult<FanboxPostDetail> = withContext(dispatcher) {
            try {
                service.parsePostDetail(RESPONSE_BODY, 200)
            } finally {
                zipline?.close()
            }
        }

        // Cached files are named by an internal id, so a module is told from the manifest by its content.
        fun cachedModuleFiles(remote: SignedBundle): List<Path> {
            val moduleHashes = remote.httpClient.moduleHashes
            val files = fileSystem
                .listRecursively(root)
                .filter { path -> fileSystem.metadata(path).isRegularFile }
                .filter { path -> fileSystem.read(path) { readByteString() }.sha256() in moduleHashes }
                .toList()
            check(files.size == moduleHashes.size) { "Expected ${moduleHashes.size} cached modules under $root, found $files" }

            return files
        }

        // Only the manifest is out of reach, so a module the cache lacks could still be downloaded.
        suspend fun assertCachedBundleDoesNotLoad(remote: SignedBundle) {
            remote.httpClient.downloadedUrls.clear()
            remote.httpClient.manifestReachable = false

            assertFailsWith<IllegalStateException> { loadGuest(remote) }
            assertEquals(listOf(MANIFEST_URL), remote.httpClient.downloadedUrls)
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
        val moduleHashes = modulesByFileName.values.map(ByteString::sha256).toSet()
        var manifestReachable = true

        override suspend fun download(url: String, requestHeaders: List<Pair<String, String>>): ByteString {
            downloadedUrls += url

            if (url == MANIFEST_URL) {
                check(manifestReachable) { "manifest unavailable" }
                return manifestJson.encodeToByteArray().toByteString()
            }

            return modulesByFileName[url.substringAfterLast('/')] ?: error("Unexpected bundle URL: $url")
        }
    }

    private object UnreachableZiplineHttpClient : ZiplineHttpClient() {
        override suspend fun download(url: String, requestHeaders: List<Pair<String, String>>): ByteString = error("network unavailable")
    }

    private companion object {
        const val FIXED_BUNDLE_DIRECTORY = "GuestProduction"
        const val TRUSTED_KEY_NAME = "test-key"
        const val RECOVERY_KEY_NAME = "test-recovery-key"
        const val MANIFEST_URL = "https://example.invalid/manifest.zipline.json"
        const val RESPONSE_BODY =
            """{"body":{"post":{"body":{"text":"OTA fixture"},"commentCount":0,"creatorId":"fixture-creator","excerpt":"Fixture excerpt","feeRequired":0,"hasAdultContent":false,"id":"10000001","imageForShare":"https://example.invalid/share.png","isLiked":false,"isRestricted":false,"likeCount":0,"nextPost":null,"prevPost":null,"publishedDatetime":"2026-01-01T00:00:00Z","tags":[],"title":"Fixture title","type":"text","updatedDatetime":"2026-01-01T00:00:00Z","coverImageUrl":null,"user":null}}}"""
    }
}
