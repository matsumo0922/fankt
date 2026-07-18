package me.matsumo.fankt.fanbox

import de.jensklingenberg.ktorfit.Ktorfit
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import me.matsumo.fankt.fanbox.datasource.createFanboxPostApi
import me.matsumo.fankt.fanbox.datasource.createFanboxUserApi
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxUserMapper
import me.matsumo.fankt.fanbox.fixture.FanboxTolerantListJsonFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.github.aakira.napier.LogLevel as NapierLogLevel

class FanboxTolerantListDecodingTest {

    @Test
    fun productionHomeApiKeepsTwoValidPostsAndSkipsOneBrokenItem() = runBlocking {
        val formatter = createFanboxJson()
        val client = mockClient(FanboxTolerantListJsonFixtures.timelineMixed)
        try {
            val api = ktorfit(client).createFanboxPostApi()
            val entity = api.getHomePosts("3", null, null, null, null)
            val result = FanboxPostMapper(FanboxListItemDecoder(formatter)).map(entity, "post.listHome")

            assertEquals(listOf("tolerant-post-1", "tolerant-post-2"), result.value.contents.map { it.id.value })
            assertEquals(listOf(FanboxListItemSchemaMismatch("post.listHome", listOf(1))), result.mismatches)
            assertEquals(3, result.value.cursor?.limit)
        } finally {
            client.close()
        }
    }

    @Test
    fun productionBellApiKeepsTwoValidBellsAndSkipsKnownTypeWithMissingField() = runBlocking {
        val formatter = createFanboxJson()
        val client = mockClient(FanboxTolerantListJsonFixtures.bellMixed)
        try {
            val api = ktorfit(client).createFanboxUserApi()
            val postMapper = FanboxPostMapper(FanboxListItemDecoder(formatter))
            val creatorMapper = FanboxCreatorMapper(FanboxListItemDecoder(formatter))
            val mapper = FanboxUserMapper(postMapper, creatorMapper, FanboxListItemDecoder(formatter))
            val result = mapper.map(api.getBells(), "bell.list")

            assertEquals(2, result.value.contents.size)
            assertEquals(listOf(FanboxListItemSchemaMismatch("bell.list", listOf(1))), result.mismatches)
            assertEquals(2, result.value.nextPage)
        } finally {
            client.close()
        }
    }

    @Test
    fun brokenNestedReplyKeepsRootAndSiblingReplies() {
        val formatter = createFanboxJson()
        val entity = formatter.decodeFromString<me.matsumo.fankt.fanbox.domain.entity.FanboxPostCommentListEntity>(
            FanboxTolerantListJsonFixtures.commentsWithBrokenReply,
        )
        val result = FanboxPostMapper(FanboxListItemDecoder(formatter)).map(entity, "post.getComments")

        assertEquals(1, result.value.contents.size)
        assertEquals(listOf("reply-1", "reply-2"), result.value.contents.single().replies.map { it.id.value })
        assertEquals(listOf(FanboxListItemSchemaMismatch("post.getComments", listOf(0, 1))), result.mismatches)
    }

    @Test
    fun strictSupportingPlansKeepsActualResponseStatusWhileTolerantRouteReturnsPartialResult() = runBlocking {
        val formatter = createFanboxJson()
        val client = mockClient(
            body = FanboxTolerantListJsonFixtures.supportingPlansMixed,
            status = HttpStatusCode.PartialContent,
        )
        try {
            val api = ktorfit(client).createFanboxUserApi()
            val strictFailure = assertFailsWith<FanboxException.SchemaMismatch> { api.getSupportedPlans() }
            assertEquals(HttpStatusCode.PartialContent.value, strictFailure.statusCode)

            val tolerant = FanboxCreatorMapper(FanboxListItemDecoder(formatter)).map(
                api.getSupportedPlansTolerant(),
                "plan.listSupporting",
            )
            assertEquals(listOf("tolerant-plan-1", "tolerant-plan-2"), tolerant.value.map { it.id.value })
            assertEquals(listOf(FanboxListItemSchemaMismatch("plan.listSupporting", listOf(1))), tolerant.mismatches)
        } finally {
            client.close()
        }
    }

    @Test
    fun strictSupportingPlansConvertsDomainMappingDriftToSchemaMismatch() = runBlocking {
        val formatter = createFanboxJson()
        val client = mockClient(
            body = FanboxTolerantListJsonFixtures.supportingPlanInvalidUserId,
            status = HttpStatusCode.PartialContent,
        )
        try {
            val api = ktorfit(client).createFanboxUserApi()

            val failure = assertFailsWith<FanboxException.SchemaMismatch> { api.getSupportedPlans() }

            assertEquals(HttpStatusCode.PartialContent.value, failure.statusCode)
            assertEquals("plan.listSupporting", failure.endpoint)

            val tolerant = FanboxCreatorMapper(FanboxListItemDecoder(formatter)).map(
                api.getSupportedPlansTolerant(),
                "plan.listSupporting",
            )
            assertTrue(tolerant.value.isEmpty())
            assertEquals(listOf(FanboxListItemSchemaMismatch("plan.listSupporting", listOf(0))), tolerant.mismatches)
        } finally {
            client.close()
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
        val antilog = object : Antilog() {
            override fun performLog(
                priority: NapierLogLevel,
                tag: String?,
                throwable: Throwable?,
                message: String?,
            ) {
                message?.let(logs::add)
            }
        }
        Napier.base(antilog)

        try {
            val formatter = createFanboxJson()
            val entity = formatter.decodeFromString<me.matsumo.fankt.fanbox.domain.entity.FanboxPostListEntity>(
                FanboxTolerantListJsonFixtures.timelineMixed,
            )
            FanboxPostMapper(FanboxListItemDecoder(formatter, includeRawFragment = false))
                .map(entity, "post.listHome")
            val privateLog = logs.last()
            assertTrue("endpoint: post.listHome" in privateLog)
            assertTrue("indexPath: [1]" in privateLog)
            assertFalse("private item content" in privateLog)
            assertFalse("fixture-credential" in privateLog)

            FanboxPostMapper(FanboxListItemDecoder(formatter, includeRawFragment = true))
                .map(entity, "post.listHome")
            val diagnosticLog = logs.last()
            assertTrue("[REDACTED]" in diagnosticLog)
            assertFalse("fixture-credential-must-be-redacted" in diagnosticLog)
            assertTrue(diagnosticLog.length <= FanboxExceptionFactory.MAX_RAW_BODY_LENGTH + 128)
        } finally {
            Napier.takeLogarithm(antilog)
        }
    }

    private fun ktorfit(client: HttpClient): Ktorfit = Ktorfit.Builder()
        .baseUrl("https://api.fanbox.cc/")
        .httpClient(client)
        .build()

    private fun mockClient(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient = HttpClient(
        MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    ) {
        configureFanboxClient(
            formatter = createFanboxJson(),
            source = FanboxDiagnosticSource.LibraryGenerated,
            logLevel = LogLevel.NONE,
        )
    }
}
