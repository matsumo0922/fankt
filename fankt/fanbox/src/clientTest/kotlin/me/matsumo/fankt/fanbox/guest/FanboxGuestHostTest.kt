package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.ZiplineApiMismatchException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.FanboxGuestEvent
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import me.matsumo.fankt.fanbox.repository.FanboxPostRepository
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.response.FanboxResponses
import me.matsumo.fankt.fanbox.transport.FanboxRawResponse
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FanboxGuestHostTest {

    @Test
    fun guestResultIsReturned() = runBlocking {
        val fixture = GuestFixture(parse = { _, _ -> GuestParseResult.Success(GUEST_POST) })

        fixture.use {
            assertEquals(GUEST_POST, it.getPostDetail())
            assertEquals(1, it.requests.size)
            assertEquals(listOf<FanboxGuestEvent>(FanboxGuestEvent.Loaded(BUNDLE_VERSION)), it.events)
        }
    }

    @Test
    fun aRejectedResponseFailsAsASchemaMismatch() = runBlocking {
        val fixture = GuestFixture(parse = { _, _ -> GuestParseResult.SchemaMismatch("changed schema") })

        fixture.use {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> { it.getPostDetail() }

            assertEquals("post.info", failure.endpoint)
            assertEquals(200, failure.statusCode)
            assertEquals("changed schema", failure.cause?.message)
            assertEquals(1, it.requests.size)
            assertTrue(it.diagnostics.single().startsWith("Zipline guest parseResponse failed"))
            assertEquals(FanboxGuestEvent.SchemaMismatch("post.info"), it.events.last())
        }
    }

    @Test
    fun guestParseFailuresFailTheCall() = runBlocking {
        val failures = listOf<Pair<(String, Int) -> GuestParseResult<FanboxPostDetail>, String>>(
            { _: String, _: Int -> GuestParseResult.GuestFailure("broken guest") } to "GuestFailure",
            { _: String, _: Int -> throw SerializationException("unexpected discriminator") } to "SerializationException",
            { _: String, _: Int -> throw ZiplineApiMismatchException("no such method") } to "ZiplineApiMismatchException",
        )

        for ((parse, errorType) in failures) {
            GuestFixture(parse = parse).use { fixture ->
                val failure = assertFailsWith<FanboxException.GuestFailure> { fixture.getPostDetail() }

                assertEquals("post.info", failure.endpoint)
                assertEquals(200, failure.statusCode)
                // The request is sent once; nothing retries it with another parser.
                assertEquals(1, fixture.requests.size)
                assertTrue(fixture.diagnostics.single().startsWith("Zipline guest parseResponse failed"))
                assertEquals(FanboxGuestEvent.CallFailed("post.info", "parseResponse", errorType), fixture.events.last())
            }
        }
    }

    @Test
    fun aRequestBuildFailureFailsBeforeAnyRequest() = runBlocking {
        val fixture = GuestFixture(
            parse = { _, _ -> error("parse must not run without a request") },
            buildRequest = { throw ZiplineApiMismatchException("no such method") },
        )

        fixture.use {
            val failure = assertFailsWith<FanboxException.GuestFailure> { it.getPostDetail() }

            assertEquals(null, failure.statusCode)
            assertTrue(it.requests.isEmpty())
            assertTrue(it.diagnostics.single().startsWith("Zipline guest buildRequest failed"))
            assertEquals(FanboxGuestEvent.CallFailed("post.info", "buildRequest", "ZiplineApiMismatchException"), it.events.last())
        }
    }

    @Test
    fun aFailedCallDoesNotStopTheGuest() = runBlocking {
        var parseCalls = 0
        val fixture = GuestFixture(
            parse = { _, _ ->
                parseCalls += 1
                if (parseCalls == 1) GuestParseResult.GuestFailure("broken post") else GuestParseResult.Success(GUEST_POST)
            },
        )

        fixture.use {
            assertFailsWith<FanboxException.GuestFailure> { it.getPostDetail() }
            assertEquals(GUEST_POST, it.getPostDetail())
        }
    }

    @Test
    fun callsWaitForTheGuestToLoad() = runBlocking {
        val loadGate = CompletableDeferred<Unit>()
        val fixture = GuestFixture(
            parse = { _, _ -> GuestParseResult.Success(GUEST_POST) },
            beforeLoad = { loadGate.await() },
        )

        fixture.use {
            // Undispatched, the call runs until it suspends waiting for the load.
            val call = async(start = CoroutineStart.UNDISPATCHED) { it.getPostDetail() }

            assertTrue(it.requests.isEmpty())

            loadGate.complete(Unit)

            assertEquals(GUEST_POST, call.await())
        }
    }

    @Test
    fun aLoadFailureFailsEveryCallWithoutLoadingAgain() = runBlocking {
        var loads = 0
        val fixture = GuestFixture(
            parse = { _, _ -> error("the guest failed to load") },
            beforeLoad = {
                loads += 1
                error("manifest unreachable")
            },
        )

        fixture.use {
            repeat(2) { _ ->
                val failure = assertFailsWith<FanboxException.GuestFailure> { it.getPostDetail() }

                assertEquals("manifest unreachable", failure.cause?.message)
            }

            assertEquals(1, loads)
            assertTrue(it.requests.isEmpty())
            assertTrue(it.diagnostics.single().startsWith("Zipline guest load failed"))
            assertEquals(listOf<FanboxGuestEvent>(FanboxGuestEvent.LoadFailed("IllegalStateException")), it.events)
        }
    }

    @Test
    fun aCallerCancelledWhileWaitingDoesNotCancelTheLoad() = runBlocking {
        val loadGate = CompletableDeferred<Unit>()
        val fixture = GuestFixture(
            parse = { _, _ -> GuestParseResult.Success(GUEST_POST) },
            beforeLoad = { loadGate.await() },
        )

        fixture.use {
            val abandoned = async(start = CoroutineStart.UNDISPATCHED) { it.getPostDetail() }
            abandoned.cancel()
            loadGate.complete(Unit)

            assertEquals(GUEST_POST, it.getPostDetail())
        }
    }

    @Test
    fun aFailingListenerDoesNotAffectTheGuest() = runBlocking {
        val fixture = GuestFixture(
            parse = { _, _ -> GuestParseResult.Success(GUEST_POST) },
            eventListener = { error("cookie=secret") },
        )

        fixture.use {
            assertEquals(GUEST_POST, it.getPostDetail())
            assertTrue(it.diagnostics.any { message -> message == "Guest event listener failed (IllegalStateException)" })
            // A listener's own message may carry anything, so only its type is reported.
            assertTrue(it.diagnostics.none { message -> "secret" in message })
        }
    }

    @Test
    fun cancellationIsNotTreatedAsAGuestFailure() = runBlocking {
        val fixture = GuestFixture(parse = { _, _ -> throw CancellationException("caller went away") })

        fixture.use {
            assertFailsWith<CancellationException> { it.getPostDetail() }
            assertTrue(it.diagnostics.isEmpty())
        }
    }

    @Test
    fun sealedPostBodyRoundTripsInGuestPayload() {
        val post = FanboxResponses.postDetail(FanboxPostJsonFixtures.postInfoArticleEmbeds, 200)
        val encoded = Json.encodeToString<GuestParseResult<FanboxPostDetail>>(GuestParseResult.Success(post))
        val decoded = Json.decodeFromString<GuestParseResult<FanboxPostDetail>>(encoded)

        assertEquals(post, assertIs<GuestParseResult.Success<FanboxPostDetail>>(decoded).value)
    }

    /** 偽の guest を持つ host と、それを使う repository の組。 */
    private class GuestFixture(
        parse: (String, Int) -> GuestParseResult<FanboxPostDetail>,
        buildRequest: (String) -> RequestDescriptor = { postId -> FanboxEndpoints.postDetail(FanboxPostId(postId)) },
        beforeLoad: suspend () -> Unit = {},
        eventListener: ((FanboxGuestEvent) -> Unit)? = null,
    ) : AutoCloseable {
        val requests = mutableListOf<RequestDescriptor>()
        val diagnostics = mutableListOf<String>()
        val events = mutableListOf<FanboxGuestEvent>()
        private val requestExecutor = FanboxRequestExecutor { descriptor ->
            requests += descriptor
            FanboxRawResponse(statusCode = 200, headers = emptyMap(), bodyText = FanboxPostJsonFixtures.postInfoTextHybrid)
        }
        private val host = FanboxGuestHost(
            requestExecutor = requestExecutor,
            diagnosticSink = FanboxDiagnosticSink(diagnostics::add),
            eventListener = eventListener ?: { event -> events += event },
        ) {
            beforeLoad()
            LoadedGuest(StubGuestService(buildRequest, parse), version = BUNDLE_VERSION)
        }

        // Unconfined, so that a call reaches the host without first waiting for another thread.
        private val repository = FanboxPostRepository(
            requestExecutor = requestExecutor,
            ioDispatcher = Dispatchers.Unconfined,
            guestHost = host,
        )

        suspend fun getPostDetail(): FanboxPostDetail = repository.getPostDetail(POST_ID)

        override fun close() = host.close()
    }

    private class StubGuestService(
        private val buildRequest: (String) -> RequestDescriptor,
        private val parse: (String, Int) -> GuestParseResult<FanboxPostDetail>,
    ) : FanboxGuestService by RealFanboxGuestService() {
        override fun buildPostDetailRequest(postId: String): RequestDescriptor = buildRequest(postId)

        override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult<FanboxPostDetail> = parse(body, statusCode)
    }

    private companion object {
        const val BUNDLE_VERSION = "0123abc"
        val POST_ID = FanboxPostId("10000001")

        // A different post than the response the executor returns, so a test can tell the guest produced it.
        val GUEST_POST = FanboxResponses.postDetail(FanboxPostJsonFixtures.postInfoArticleA, 200)
    }
}
