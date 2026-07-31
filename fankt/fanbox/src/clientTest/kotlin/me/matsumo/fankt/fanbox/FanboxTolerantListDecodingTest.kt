package me.matsumo.fankt.fanbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.domain.model.id.FanboxPostId
import me.matsumo.fankt.fanbox.fixture.FanboxTolerantListJsonFixtures
import me.matsumo.fankt.fanbox.response.FanboxDiagnosticSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FanboxTolerantListDecodingTest {

    @Test
    fun productionHomeExecutorKeepsTwoValidPostsAndReportsOneBrokenItem() = runBlocking {
        val requests = mutableListOf<Int>()
        val fanbox = productionFanbox(FanboxTolerantListJsonFixtures.timelineMixed, requests)
        val mismatches = mutableListOf<FanboxListItemSchemaMismatch>()
        try {
            val result = fanbox.getHomePosts(cursor = null, onItemSchemaMismatch = mismatches::add)

            assertEquals(listOf("tolerant-post-1", "tolerant-post-2"), result.contents.map { it.id.value })
            assertEquals(listOf(FanboxListItemSchemaMismatch("post.listHome", listOf(1))), mismatches)
            assertEquals(3, result.cursor?.limit)
            assertEquals(listOf(0), requests)
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun productionBellExecutorKeepsTwoValidBellsAndSkipsKnownTypeWithMissingField() = runBlocking {
        val requests = mutableListOf<Int>()
        val fanbox = productionFanbox(FanboxTolerantListJsonFixtures.bellMixed, requests)
        val mismatches = mutableListOf<FanboxListItemSchemaMismatch>()
        try {
            val result = fanbox.getBells(page = 0, onItemSchemaMismatch = mismatches::add)

            assertEquals(2, result.contents.size)
            assertEquals(listOf(FanboxListItemSchemaMismatch("bell.list", listOf(1))), mismatches)
            assertEquals(2, result.nextPage)
            assertEquals(listOf(0), requests)
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun productionCommentExecutorKeepsRootAndSiblingRepliesAndReportsNestedPath() = runBlocking {
        val requests = mutableListOf<Int>()
        val fanbox = productionFanbox(FanboxTolerantListJsonFixtures.commentsWithBrokenReply, requests)
        val mismatches = mutableListOf<FanboxListItemSchemaMismatch>()
        try {
            val result = fanbox.getPostComment(
                postId = FanboxPostId("fixture-post"),
                offset = 0,
                onItemSchemaMismatch = mismatches::add,
            )

            assertEquals(1, result.contents.size)
            assertEquals(listOf("reply-1", "reply-2"), result.contents.single().replies.map { it.id.value })
            assertEquals(listOf(FanboxListItemSchemaMismatch("post.getComments", listOf(0, 1))), mismatches)
            assertEquals(listOf(0), requests)
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun strictSupportingPlansKeepsActualResponseStatusWhileTolerantRouteReturnsPartialResult() = runBlocking {
        val requests = mutableListOf<Int>()
        val fanbox = productionFanbox(
            body = FanboxTolerantListJsonFixtures.supportingPlansMixed,
            requestClientIndexes = requests,
            status = HttpStatusCode.PartialContent,
        )
        val mismatches = mutableListOf<FanboxListItemSchemaMismatch>()
        try {
            val strictFailure = assertFailsWith<FanboxException.SchemaMismatch> { fanbox.getSupportedPlans() }
            assertEquals(HttpStatusCode.PartialContent.value, strictFailure.statusCode)

            val tolerant = fanbox.getSupportedPlans(mismatches::add)
            assertEquals(listOf("tolerant-plan-1", "tolerant-plan-2"), tolerant.map { it.id.value })
            assertEquals(listOf(FanboxListItemSchemaMismatch("plan.listSupporting", listOf(1))), mismatches)
            assertEquals(listOf(0, 0), requests)
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun strictSupportingPlansConvertsDomainMappingDriftToSchemaMismatch() = runBlocking {
        val requests = mutableListOf<Int>()
        val fanbox = productionFanbox(
            body = FanboxTolerantListJsonFixtures.supportingPlanInvalidUserId,
            requestClientIndexes = requests,
            status = HttpStatusCode.PartialContent,
        )
        val mismatches = mutableListOf<FanboxListItemSchemaMismatch>()
        try {
            val failure = assertFailsWith<FanboxException.SchemaMismatch> { fanbox.getSupportedPlans() }

            assertEquals(HttpStatusCode.PartialContent.value, failure.statusCode)
            assertEquals("plan.listSupporting", failure.endpoint)

            val tolerant = fanbox.getSupportedPlans(mismatches::add)
            assertTrue(tolerant.isEmpty())
            assertEquals(listOf(FanboxListItemSchemaMismatch("plan.listSupporting", listOf(0))), mismatches)
            assertEquals(listOf(0, 0), requests)
        } finally {
            fanbox.close()
        }
    }

    @Test
    fun creatorSharedEntityUsesTheProvidedEndpointLabel() {
        val formatter = createFanboxJson()
        val entity = formatter.decodeFromString<me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorListEntity>(
            FanboxTolerantListJsonFixtures.creatorsMixed,
        )
        val mapper = FanboxCreatorMapper(FanboxListItemDecoder(formatter))

        listOf("creator.listFollowing", "creator.listPixiv", "creator.listRecommended").forEach { endpoint ->
            val result = mapper.map(entity, endpoint)
            assertEquals(2, result.value.size)
            assertEquals(listOf(FanboxListItemSchemaMismatch(endpoint, listOf(1))), result.mismatches)
        }
    }

    @Test
    fun creatorProfileItemDecodeFailureKeepsTolerantListBoundary() {
        val formatter = createFanboxJson()
        val entity = formatter.decodeFromString<me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorListEntity>(
            FanboxTolerantListJsonFixtures.creatorsWithInvalidProfileItem,
        )
        val mapper = FanboxCreatorMapper(FanboxListItemDecoder(formatter))

        listOf("creator.listFollowing", "creator.listPixiv", "creator.listRecommended").forEach { endpoint ->
            val result = mapper.map(entity, endpoint)
            assertEquals(listOf("profile-creator-1", "profile-creator-2"), result.value.map { it.creatorId.value })
            assertEquals(listOf(FanboxListItemSchemaMismatch(endpoint, listOf(1))), result.mismatches)
        }
    }

    @Test
    fun callbackIsSynchronousCallLocalAndPropagatesConsumerFailure() {
        val first = FanboxListItemSchemaMismatch("post.listHome", listOf(0))
        val second = FanboxListItemSchemaMismatch("post.listHome", listOf(2))
        val events = mutableListOf<FanboxListItemSchemaMismatch>()

        val value = FanboxTolerantResult("value", listOf(first, second)).notifyMismatches(events::add)

        assertEquals("value", value)
        assertEquals(listOf(first, second), events)
        assertFailsWith<IllegalArgumentException> {
            FanboxTolerantResult("unused", listOf(first)).notifyMismatches { throw IllegalArgumentException("consumer") }
        }
    }

    @Test
    fun rawFragmentIsDefaultPrivateAndLoggingOptInIsBoundedAndRedacted() {
        val logs = mutableListOf<String>()
        val formatter = createFanboxJson()
        val entity = formatter.decodeFromString<me.matsumo.fankt.fanbox.domain.entity.FanboxPostListEntity>(
            FanboxTolerantListJsonFixtures.timelineMixed,
        )
        FanboxPostMapper(
            FanboxListItemDecoder(
                formatter = formatter,
                includeRawFragment = false,
                diagnosticSink = FanboxDiagnosticSink(logs::add),
            ),
        ).map(entity, "post.listHome")
        val privateLog = logs.last()
        assertTrue("endpoint: post.listHome" in privateLog)
        assertTrue("indexPath: [1]" in privateLog)
        assertFalse("private item content" in privateLog)
        assertFalse("fixture-credential" in privateLog)

        FanboxPostMapper(
            FanboxListItemDecoder(
                formatter = formatter,
                includeRawFragment = true,
                diagnosticSink = FanboxDiagnosticSink(logs::add),
            ),
        ).map(entity, "post.listHome")
        val diagnosticLog = logs.last()
        assertTrue("[REDACTED]" in diagnosticLog)
        assertFalse("fixture-credential-must-be-redacted" in diagnosticLog)
        assertTrue(diagnosticLog.length <= FanboxExceptionFactory.MAX_RAW_BODY_LENGTH + 128)
    }

    private fun productionFanbox(
        body: String,
        requestClientIndexes: MutableList<Int>,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): Fanbox {
        var clientCount = 0
        val clientFactory = FanboxHttpClientFactory { block ->
            val clientIndex = clientCount++
            HttpClient(
                MockEngine {
                    requestClientIndexes += clientIndex
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
                block,
            )
        }
        val csrfToken = MutableStateFlow<String?>(null)
        return Fanbox(
            dependencies = FanboxDependencies(
                cookieStorage = AcceptAllCookiesStorage(),
                cookies = emptyFlow(),
                csrfToken = csrfToken,
                getCsrfToken = { csrfToken.value },
                setCsrfToken = { csrfToken.value = it },
                clearCsrfToken = { csrfToken.value = null },
                overrideFanboxSessionId = {},
                addCookie = {},
                replaceCookies = {},
            ),
            clientFactory = clientFactory,
            ioDispatcher = Dispatchers.Default,
        )
    }
}
