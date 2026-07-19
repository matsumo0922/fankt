package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FanboxDownloadTest {

    @Test
    fun exactNonJpegUrlStreamsAndReportsProgressThroughPublicPath() = runBlocking {
        val payload = "fixture-payload"
        val fixture = createFixture {
            respond(
                content = payload,
                headers = headersOf(HttpHeaders.ContentLength, payload.length.toString()),
            )
        }
        val progress = mutableListOf<Float>()
        val chunks = mutableListOf<ByteArray>()
        val url = "https://downloads.fanbox.cc/files/post/123/archive.zip?download=1"

        fixture.fanbox.download(url, progress::add, chunks::add)

        assertEquals(payload, chunks.flatten().decodeToString())
        assertEquals(1, fixture.requests.size)
        assertEquals(url, fixture.requests.single().url.toString())
        assertEquals("fixture-token", fixture.requests.single().csrfToken)
        assertTrue(fixture.requests.single().cookie.orEmpty().contains("FANBOXSESSID=fixture-session"))
        assertEquals(listOf(0f, 1f), progress)
        assertEquals(3, fixture.clients.size)
        assertTrue(fixture.requests.single().executionJob.isCompleted)
        fixture.fanbox.close()
    }

    @Test
    fun boundedOrderedChunksWaitForSlowConsumer() = runBlocking {
        val payload = ByteArray(20_000) { index -> (index % 251).toByte() }
        val fixture = createFixture {
            respond(
                content = payload,
                headers = headersOf(HttpHeaders.ContentLength, payload.size.toString()),
            )
        }
        val callbackStarted = CompletableDeferred<Unit>()
        val releaseCallback = CompletableDeferred<Unit>()
        val chunks = mutableListOf<ByteArray>()
        val progress = mutableListOf<Pair<Float, Int>>()

        val download = async {
            fixture.fanbox.download(
                url = "https://downloads.fanbox.cc/large.bin",
                onProgress = { value -> progress += value to chunks.size },
            ) { chunk ->
                chunks += chunk
                if (chunks.size == 1) {
                    callbackStarted.complete(Unit)
                    releaseCallback.await()
                }
            }
        }

        callbackStarted.await()
        assertEquals(1, chunks.size)
        releaseCallback.complete(Unit)
        download.await()

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.isNotEmpty() && it.size <= 8 * 1_024 })
        assertContentEquals(payload, chunks.flatten())
        assertEquals(0f to 0, progress.first())
        assertTrue(progress.drop(1).all { (_, deliveredChunkCount) -> deliveredChunkCount > 0 })
        assertEquals(1f, progress.last().first)
        fixture.fanbox.close()
    }

    @Test
    fun observedExternalHostsOmitCsrfAndFanboxCookies() = runBlocking {
        val fixture = createFixture()

        fixture.fanbox.download("https://pixiv.pximg.net/image.png") {}
        fixture.fanbox.download("https://fanbox.pixiv.net/images/entry/file.gif") {}

        assertEquals(listOf("pixiv.pximg.net", "fanbox.pixiv.net"), fixture.requests.map { it.url.host })
        fixture.requests.forEach { request ->
            assertNull(request.csrfToken)
            assertNull(request.cookie)
        }
        fixture.fanbox.close()
    }

    @Test
    fun invalidDestinationsAreRejectedBeforeCreatingARequest() = runBlocking {
        val fixture = createFixture()
        val invalidUrls = listOf(
            "http://downloads.fanbox.cc/file.zip",
            "https://example.com/file.zip",
            "https://fanbox.cc.evil.example/file.zip",
            "https://notfanbox.cc/file.zip",
            "https://www.pixiv.net/file.zip",
            "https://i.pximg.net/file.zip",
            "https://downloads.fanbox.cc./file.zip",
            "https://user:password@downloads.fanbox.cc/file.zip",
            "not a URL",
        )

        invalidUrls.forEach { url ->
            assertFailsWith<IllegalArgumentException>(url) {
                fixture.fanbox.download(url) {}
            }
        }

        assertTrue(fixture.requests.isEmpty())
        fixture.fanbox.close()
    }

    @Test
    fun allowedHostMatchingIsCaseInsensitive() = runBlocking {
        val fixture = createFixture()

        fixture.fanbox.download("https://DOWNLOADS.FANBOX.CC/file.psd") {}

        assertTrue(fixture.requests.single().url.host.equals("downloads.fanbox.cc", ignoreCase = true))
        fixture.fanbox.close()
    }

    @Test
    fun zeroAndUnknownContentLengthOnlyReportInitialZero() = runBlocking {
        val zeroFixture = createFixture {
            respond(content = "", headers = headersOf(HttpHeaders.ContentLength, "0"))
        }
        val zeroProgress = mutableListOf<Float>()
        zeroFixture.fanbox.download("https://downloads.fanbox.cc/empty", zeroProgress::add) {}
        assertEquals(listOf(0f), zeroProgress)
        zeroFixture.fanbox.close()

        val unknownFixture = createFixture { respond(content = "unknown") }
        val unknownProgress = mutableListOf<Float>()
        unknownFixture.fanbox.download("https://downloads.fanbox.cc/unknown", unknownProgress::add) {}
        assertEquals(listOf(0f), unknownProgress)
        unknownFixture.fanbox.close()
    }

    @Test
    fun redirectOutsideAllowlistIsRejectedBeforeSecondTransportAndReleasesPriorCall() = runBlocking {
        val fixture = createFixture { request ->
            if (request.url.host == "downloads.fanbox.cc") {
                respondRedirect("https://evil.example/file.zip")
            } else {
                error("Disallowed redirect reached transport: ${request.url}")
            }
        }

        assertFailsWith<IllegalArgumentException> {
            fixture.fanbox.download("https://downloads.fanbox.cc/file.zip") {}
        }

        assertEquals(listOf("downloads.fanbox.cc"), fixture.requests.map { it.url.host })
        assertTrue(fixture.requests.single().executionJob.isCompleted)
        fixture.fanbox.close()
    }

    @Test
    fun allowedRedirectIsRevalidatedAndRemovesCsrfBeforeExternalTransport() = runBlocking {
        val fixture = createFixture { request ->
            if (request.url.host == "downloads.fanbox.cc") {
                respondRedirect("https://pixiv.pximg.net/redirected.png")
            } else {
                respond("redirected")
            }
        }
        val chunks = mutableListOf<ByteArray>()

        fixture.fanbox.download("https://downloads.fanbox.cc/original.png", onChunk = chunks::add)

        assertEquals("redirected", chunks.flatten().decodeToString())
        assertEquals(listOf("downloads.fanbox.cc", "pixiv.pximg.net"), fixture.requests.map { it.url.host })
        assertEquals("fixture-token", fixture.requests.first().csrfToken)
        assertNull(fixture.requests.last().csrfToken)
        assertNull(fixture.requests.last().cookie)
        fixture.fanbox.close()
    }

    @Test
    fun downloadHttpFailureUsesDownloadDiagnosticWithoutResponseFragment() = runBlocking {
        val fixture = createFixture { respond("download denied", HttpStatusCode.Forbidden) }

        val error = assertFailsWith<FanboxException.Forbidden> {
            fixture.fanbox.download("https://downloads.fanbox.cc/private.zip") {}
        }

        assertEquals("download", error.endpoint)
        assertNull(error.rawBody)
        assertEquals(1, fixture.requests.size)
        fixture.fanbox.close()
    }

    @Test
    fun midStreamTransportFailureIsNormalizedAfterDeliveredChunk() = runBlocking {
        val firstChunkDelivered = CompletableDeferred<Unit>()
        val transportFailure = IOException("mid-stream failure")
        val fixture = createFixture { request ->
            val channel = ByteChannel(autoFlush = true)
            CoroutineScope(request.executionContext).launch {
                channel.writeFully("first".encodeToByteArray())
                firstChunkDelivered.await()
                channel.cancel(transportFailure)
            }
            respond(channel)
        }
        val chunks = mutableListOf<ByteArray>()

        val error = assertFailsWith<FanboxException.Network> {
            fixture.fanbox.download("https://downloads.fanbox.cc/failing.bin") { chunk ->
                chunks += chunk
                firstChunkDelivered.complete(Unit)
            }
        }

        assertEquals("download", error.endpoint)
        assertEquals("first", chunks.flatten().decodeToString())
        assertIs<IOException>(error.cause)
        assertTrue(fixture.requests.single().executionJob.isCompleted)
        fixture.fanbox.close()
    }

    @Test
    fun callbackFailureKeepsIdentityAndStopsProgressBeforeChunkCompletion() = runBlocking {
        val fixture = createFixture {
            respond(content = "payload", headers = headersOf(HttpHeaders.ContentLength, "7"))
        }
        val callbackFailure = IllegalStateException("consumer failed")
        val progress = mutableListOf<Float>()

        val actual = assertFailsWith<IllegalStateException> {
            fixture.fanbox.download("https://downloads.fanbox.cc/file.bin", progress::add) {
                throw callbackFailure
            }
        }

        assertSame(callbackFailure, actual)
        assertEquals(listOf(0f), progress)
        assertTrue(fixture.requests.single().executionJob.isCompleted)
        fixture.fanbox.close()
    }

    @Test
    fun callbackCancellationKeepsIdentityAndReleasesResponse() = runBlocking {
        val fixture = createFixture()
        val cancellation = CancellationException("consumer cancelled")

        val actual = assertFailsWith<CancellationException> {
            fixture.fanbox.download("https://downloads.fanbox.cc/file.bin") {
                throw cancellation
            }
        }

        assertSame(cancellation, actual)
        assertTrue(fixture.requests.single().executionJob.isCompleted)
        fixture.fanbox.close()
    }

    @Test
    fun downloadAcceptsCoroutineContextWithoutJob() = runBlocking {
        val fixture = createFixture { respond("jobless") }
        val chunks = mutableListOf<ByteArray>()

        val result = startWithContext(EmptyCoroutineContext) {
            fixture.fanbox.download(
                url = "https://downloads.fanbox.cc/jobless.bin",
                onChunk = chunks::add,
            )
        }.await()

        result.getOrThrow()
        assertEquals("jobless", chunks.flatten().decodeToString())
        assertTrue(fixture.requests.single().executionJob.isCompleted)
        fixture.fanbox.close()
    }

    @Test
    fun ownerClosePreventsNewDownloadWork() = runBlocking {
        val fixture = createFixture()
        fixture.fanbox.close()

        assertFailsWith<IllegalStateException> {
            fixture.fanbox.download("https://downloads.fanbox.cc/after-close.zip") {}
        }
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun ownerCloseCancelsActiveDownloadAndReleasesResponse() = runBlocking {
        val callbackStarted = CompletableDeferred<Unit>()
        val fixture = createFixture()
        val download = async {
            fixture.fanbox.download("https://downloads.fanbox.cc/file.bin") {
                callbackStarted.complete(Unit)
                awaitCancellation()
            }
        }

        callbackStarted.await()
        fixture.fanbox.close()

        assertFailsWith<CancellationException> { download.await() }
        assertTrue(fixture.requests.single().executionJob.isCompleted)
    }

    @Test
    fun ownerCloseCancelsOnlyDedicatedDownloadJob() = runBlocking {
        val callbackStarted = CompletableDeferred<Unit>()
        val fixture = createFixture()
        val parentJob = Job()
        val completion = startWithContext(parentJob + Dispatchers.Default) {
            fixture.fanbox.download("https://downloads.fanbox.cc/file.bin") {
                callbackStarted.complete(Unit)
                awaitCancellation()
            }
        }

        callbackStarted.await()
        fixture.fanbox.close()

        assertIs<CancellationException>(completion.await().exceptionOrNull())
        assertTrue(parentJob.isActive)
        assertTrue(fixture.requests.single().executionJob.isCompleted)
        parentJob.cancel()
    }

    @Test
    fun pureValidatorClassifiesAllowedAndDisallowedHosts() {
        listOf(
            "https://fanbox.cc/file.zip",
            "https://downloads.fanbox.cc/file.zip",
            "https://pixiv.pximg.net/file.png",
            "https://fanbox.pixiv.net/file.gif",
        ).forEach { url ->
            assertEquals(Url(url), parseFanboxDownloadUrl(url))
        }

        val failure = assertIs<IllegalArgumentException>(
            runCatching { parseFanboxDownloadUrl("::invalid::") }.exceptionOrNull(),
        )
        assertEquals(IllegalArgumentException::class, failure::class)
    }

    private suspend fun createFixture(
        token: String? = "fixture-token",
        handler: MockRequestHandler = { respond("ok") },
    ): Fixture {
        val requests = mutableListOf<RecordedRequest>()
        val clients = mutableListOf<HttpClient>()
        val cookieStorage = AcceptAllCookiesStorage().also { storage ->
            storage.addCookie(
                requestUrl = Url("https://www.fanbox.cc"),
                cookie = Cookie(
                    name = "FANBOXSESSID",
                    value = "fixture-session",
                    domain = ".fanbox.cc",
                    path = "/",
                    secure = true,
                ),
            )
        }
        val clientFactory = FanboxHttpClientFactory { block ->
            HttpClient(
                MockEngine { request ->
                    requests += request.record()
                    handler(request)
                },
                block,
            ).also(clients::add)
        }
        val latestToken = MutableStateFlow(token)
        val dependencies = FanboxDependencies(
            cookieStorage = cookieStorage,
            cookies = emptyFlow(),
            csrfToken = latestToken,
            getCsrfToken = { latestToken.value },
            setCsrfToken = { latestToken.value = it },
            clearCsrfToken = { latestToken.value = null },
            overrideFanboxSessionId = {},
            addCookie = {},
            replaceCookies = {},
        )

        return Fixture(
            fanbox = Fanbox(
                dependencies = dependencies,
                clientFactory = clientFactory,
                ioDispatcher = Dispatchers.Default,
            ),
            requests = requests,
            clients = clients,
        )
    }

    private fun <T> startWithContext(
        context: CoroutineContext,
        block: suspend () -> T,
    ): CompletableDeferred<Result<T>> {
        val completion = CompletableDeferred<Result<T>>()
        block.startCoroutine(
            object : Continuation<T> {
                override val context: CoroutineContext = context

                override fun resumeWith(result: Result<T>) {
                    completion.complete(result)
                }
            },
        )
        return completion
    }

    private fun HttpRequestData.record(): RecordedRequest = RecordedRequest(
        url = url,
        csrfToken = headers[FANBOX_CSRF_HEADER],
        cookie = headers[HttpHeaders.Cookie],
        executionJob = executionContext,
    )

    private fun List<ByteArray>.flatten(): ByteArray {
        val result = ByteArray(sumOf(ByteArray::size))
        var offset = 0
        forEach { chunk ->
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    private data class Fixture(
        val fanbox: Fanbox,
        val requests: List<RecordedRequest>,
        val clients: List<HttpClient>,
    )

    private data class RecordedRequest(
        val url: Url,
        val csrfToken: String?,
        val cookie: String?,
        val executionJob: Job,
    )
}
