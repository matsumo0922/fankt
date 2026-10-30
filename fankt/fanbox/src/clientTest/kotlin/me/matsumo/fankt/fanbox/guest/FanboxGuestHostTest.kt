package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.ZiplineApiMismatchException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.FanboxGuestEvent
import me.matsumo.fankt.fanbox.domain.model.FanboxPostDetail
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
import me.matsumo.fankt.fanbox.repository.FanboxPostRepository
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import me.matsumo.fankt.fanbox.transport.FanboxRawResponse
import me.matsumo.fankt.fanbox.transport.FanboxRequestExecutor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** guest のロードと呼び出しの失敗が、公開 API の例外として呼び出し元へ届くことを確かめる。 */
class FanboxGuestHostTest {

    @Test
    fun aRejectedResponseFailsAsASchemaMismatch() = runBlocking {
        val fixture = GuestFixture(parse = { _, _ -> GuestParseResult.SchemaMismatch("changed schema") })

        fixture.use {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> { it.getPostDetail() }

            assertEquals("post.info", failure.endpoint)
            assertEquals(200, failure.statusCode)
            assertEquals("changed schema", failure.cause?.message)
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
            assertEquals(FanboxGuestEvent.CallFailed("post.info", "buildRequest", "ZiplineApiMismatchException"), it.events.last())
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
            assertEquals(listOf<FanboxGuestEvent>(FanboxGuestEvent.LoadFailed("IllegalStateException")), it.events)
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

    /** 偽の guest を持つ host と、それを使う repository の組。 */
    private class GuestFixture(
        parse: (String, Int) -> GuestParseResult<FanboxPostDetail>,
        buildRequest: (String) -> RequestDescriptor = { postId -> FanboxEndpoints.postDetail(FanboxPostId(postId)) },
        beforeLoad: suspend () -> Unit = {},
    ) : AutoCloseable {
        val requests = mutableListOf<RequestDescriptor>()
        val diagnostics = mutableListOf<String>()
        val events = mutableListOf<FanboxGuestEvent>()
        private val requestExecutor = FanboxRequestExecutor { descriptor ->
            requests += descriptor
            FanboxRawResponse(statusCode = 200, headers = emptyMap(), bodyText = "{}")
        }
        private val host = FanboxGuestHost(
            requestExecutor = requestExecutor,
            diagnosticSink = FanboxDiagnosticSink(diagnostics::add),
            eventListener = { event -> events += event },
        ) {
            beforeLoad()
            LoadedGuest(StubGuestService(buildRequest, parse))
        }

        // Unconfined, so that a call reaches the host without first waiting for another thread.
        private val repository = FanboxPostRepository(
            guestHost = host,
            diagnosticSink = FanboxDiagnosticSink(diagnostics::add),
            ioDispatcher = Dispatchers.Unconfined,
        )

        suspend fun getPostDetail(): FanboxPostDetail = repository.getPostDetail(FanboxPostId("10000001"))

        override fun close() = host.close()
    }

    private class StubGuestService(
        private val buildRequest: (String) -> RequestDescriptor,
        private val parse: (String, Int) -> GuestParseResult<FanboxPostDetail>,
    ) : FanboxGuestService by RealFanboxGuestService() {
        override fun buildPostDetailRequest(postId: String): RequestDescriptor = buildRequest(postId)

        override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult<FanboxPostDetail> = parse(body, statusCode)
    }
}
