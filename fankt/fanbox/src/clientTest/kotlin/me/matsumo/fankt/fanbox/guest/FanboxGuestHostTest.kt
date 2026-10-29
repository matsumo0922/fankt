package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.ZiplineApiMismatchException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.matsumo.fankt.fanbox.FanboxException
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FanboxGuestHostTest {

    @Test
    fun guestResultIsReturned() = runBlocking {
        val fixture = GuestFixture(parse = { _, _ -> GuestParseResult.Success(GUEST_POST) })

        fixture.use {
            assertEquals(GUEST_POST, it.getPostDetail())
            assertEquals(1, it.requests.size)
        }
    }

    @Test
    fun guestParseFailuresUseTheBuiltInParserOnTheSameResponse() = runBlocking {
        val failures = listOf<(String, Int) -> GuestParseResult>(
            { _, _ -> GuestParseResult.SchemaMismatch("changed schema") },
            { _, _ -> GuestParseResult.GuestFailure("broken guest") },
            { _, _ -> throw SerializationException("unexpected discriminator") },
            { _, _ -> throw ZiplineApiMismatchException("no such method") },
        )

        for (parse in failures) {
            GuestFixture(parse = parse).use { fixture ->
                assertEquals(BUILT_IN_POST, fixture.getPostDetail())
                // The built-in parser reads the response already received instead of requesting it again.
                assertEquals(1, fixture.requests.size)
                assertTrue(fixture.diagnostics.single().startsWith("Zipline guest parsePostDetail failed"))
            }
        }
    }

    @Test
    fun responseRejectedByBothParsersThrowsTheBuiltInSchemaMismatch() = runBlocking {
        val fixture = GuestFixture(
            parse = { _, _ -> GuestParseResult.SchemaMismatch("changed schema") },
            responseBody = """{"body":{}}""",
        )

        fixture.use {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> { it.getPostDetail() }

            assertEquals("post.info", failure.endpoint)
            assertEquals(1, it.requests.size)
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
            assertEquals(BUILT_IN_POST, it.getPostDetail())
            assertEquals(GUEST_POST, it.getPostDetail())
        }
    }

    @Test
    fun aRequestBuildFailureUsesTheBuiltInPathForTheWholeCall() = runBlocking {
        val fixture = GuestFixture(
            parse = { _, _ -> error("parse must not run once the request was built by the built-in path") },
            buildRequest = { throw ZiplineApiMismatchException("no such method") },
        )

        fixture.use {
            assertEquals(BUILT_IN_POST, it.getPostDetail())
            assertEquals(listOf(FanboxEndpoints.postDetail(POST_ID)), it.requests)
            assertTrue(it.diagnostics.single().startsWith("Zipline guest buildPostDetailRequest failed"))
        }
    }

    @Test
    fun callsDoNotWaitForTheGuestToLoad() = runBlocking {
        val fixture = GuestFixture(
            parse = { _, _ -> error("the guest has not loaded") },
            load = { awaitCancellation() },
            awaitLoad = false,
        )

        fixture.use {
            assertEquals(BUILT_IN_POST, it.getPostDetail())
        }
    }

    @Test
    fun aLoadFailureUsesTheBuiltInPath() = runBlocking {
        val fixture = GuestFixture(
            parse = { _, _ -> error("the guest failed to load") },
            load = { error("manifest unreachable") },
        )

        fixture.use {
            assertEquals(BUILT_IN_POST, it.getPostDetail())
            assertTrue(it.diagnostics.single().startsWith("Zipline guest load failed"))
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
    fun aClosedHostDoesNotUseTheGuest() = runBlocking {
        val fixture = GuestFixture(parse = { _, _ -> GuestParseResult.Success(GUEST_POST) })
        fixture.host.awaitLoad()

        fixture.close()

        assertNull(fixture.host.buildPostDetailRequest(POST_ID))
    }

    @Test
    fun sealedPostBodyRoundTripsInGuestPayload() {
        val post = FanboxResponses.postDetail(FanboxPostJsonFixtures.postInfoArticleEmbeds, 200)
        val encoded = Json.encodeToString<GuestParseResult>(GuestParseResult.Success(post))
        val decoded = Json.decodeFromString<GuestParseResult>(encoded)

        assertEquals(post, assertIs<GuestParseResult.Success>(decoded).postDetail)
    }

    /** 偽の guest を持つ host と、それを使う repository の組。 */
    private class GuestFixture(
        parse: (String, Int) -> GuestParseResult,
        buildRequest: (String) -> RequestDescriptor = { postId -> FanboxEndpoints.postDetail(FanboxPostId(postId)) },
        responseBody: String = FanboxPostJsonFixtures.postInfoTextHybrid,
        load: suspend () -> LoadedGuest = { LoadedGuest(StubGuestService(buildRequest, parse)) },
        private val awaitLoad: Boolean = true,
    ) : AutoCloseable {
        val requests = mutableListOf<RequestDescriptor>()
        val diagnostics = mutableListOf<String>()
        val host = FanboxGuestHost(FanboxDiagnosticSink(diagnostics::add)) { load() }
        private val repository = FanboxPostRepository(
            requestExecutor = FanboxRequestExecutor { descriptor ->
                requests += descriptor
                FanboxRawResponse(statusCode = 200, headers = emptyMap(), bodyText = responseBody)
            },
            guestHost = host,
        )

        suspend fun getPostDetail(): FanboxPostDetail {
            if (awaitLoad) host.awaitLoad()

            return repository.getPostDetail(POST_ID)
        }

        override fun close() = host.close()
    }

    private class StubGuestService(
        private val buildRequest: (String) -> RequestDescriptor,
        private val parse: (String, Int) -> GuestParseResult,
    ) : FanboxGuestService {
        override fun buildPostDetailRequest(postId: String): RequestDescriptor = buildRequest(postId)

        override fun parsePostDetail(body: String, statusCode: Int): GuestParseResult = parse(body, statusCode)
    }

    private companion object {
        val POST_ID = FanboxPostId("10000001")
        val BUILT_IN_POST = FanboxResponses.postDetail(FanboxPostJsonFixtures.postInfoTextHybrid, 200)

        // A different post than BUILT_IN_POST, so a test can tell which path produced its result.
        val GUEST_POST = FanboxResponses.postDetail(FanboxPostJsonFixtures.postInfoArticleA, 200)
    }
}
