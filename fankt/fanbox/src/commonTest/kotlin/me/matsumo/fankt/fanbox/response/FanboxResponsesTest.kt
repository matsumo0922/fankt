package me.matsumo.fankt.fanbox.response

import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.FanboxListItemDecoder
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxUserMapper
import me.matsumo.fankt.fanbox.datasource.parser.FanboxMetadataExtractor
import me.matsumo.fankt.fanbox.domain.entity.FanboxCreatorDetailEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPaidRecordListEntity
import me.matsumo.fankt.fanbox.domain.entity.FanboxPostDetailEntity
import me.matsumo.fankt.fanbox.fixture.FanboxCreatorJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxMetadataHtmlFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxPaymentJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import me.matsumo.fankt.fanbox.fixture.FanboxTolerantListJsonFixtures
import me.matsumo.fankt.fanbox.fixture.decodeFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FanboxResponsesTest {

    @Test
    fun goldenJsonResponsesReuseProductionEntitiesAndMappers() {
        val decoder = FanboxListItemDecoder()
        val postMapper = FanboxPostMapper(decoder)
        val creatorMapper = FanboxCreatorMapper(decoder)
        val userMapper = FanboxUserMapper(postMapper, creatorMapper, decoder)

        assertEquals(
            postMapper.map(decodeFixture<FanboxPostDetailEntity>(FanboxPostJsonFixtures.postInfoArticleA)),
            FanboxResponses.postDetail(FanboxPostJsonFixtures.postInfoArticleA),
        )
        assertEquals(
            creatorMapper.map(decodeFixture<FanboxCreatorDetailEntity>(FanboxCreatorJsonFixtures.actualCreatorGet)),
            FanboxResponses.creatorDetail(FanboxCreatorJsonFixtures.actualCreatorGet),
        )
        assertEquals(
            userMapper.map(decodeFixture<FanboxPaidRecordListEntity>(FanboxPaymentJsonFixtures.paidRecords)),
            FanboxResponses.paidRecords(FanboxPaymentJsonFixtures.paidRecords),
        )
    }

    @Test
    fun homepageHtmlUsesProductionMetadataParserAndMapper() {
        val extractor = FanboxMetadataExtractor { html ->
            Regex("meta name=\"metadata\" content=\"(.*?)\">").find(html)
                ?.groupValues
                ?.get(1)
                ?.replace("&quot;", "\"")
        }

        val metadata = FanboxResponses.homepage(FanboxMetadataHtmlFixtures.home, extractor)

        assertEquals("fixture-token", metadata.csrfToken)
        assertEquals("https://api.example.invalid", metadata.apiUrl)
    }

    @Test
    fun supportingPlansKeepSeparateStrictAndTolerantOperations() {
        val logs = mutableListOf<String>()

        val strict = assertFailsWith<FanboxException.SchemaMismatch> {
            FanboxResponses.supportingPlansStrict(
                body = FanboxTolerantListJsonFixtures.supportingPlansMixed,
                statusCode = 206,
            )
        }
        val tolerant = FanboxResponses.supportingPlansTolerant(
            body = FanboxTolerantListJsonFixtures.supportingPlansMixed,
            statusCode = 206,
            diagnosticSink = FanboxDiagnosticSink(logs::add),
            includeRawFragment = true,
        )

        assertEquals("plan.listSupporting", strict.endpoint)
        assertEquals(206, strict.statusCode)
        assertEquals(listOf("tolerant-plan-1", "tolerant-plan-2"), tolerant.value.map { it.id.value })
        assertEquals(listOf(listOf(1)), tolerant.mismatches.map { it.indexPath })
        assertEquals(1, logs.size)
        assertTrue("endpoint: plan.listSupporting" in logs.single())
        assertTrue("indexPath: [1]" in logs.single())
    }

    @Test
    fun malformedSuccessfulJsonBecomesSanitizedSchemaMismatch() {
        val secret = "fixture-secret"
        val failure = assertFailsWith<FanboxException.SchemaMismatch> {
            FanboxResponses.postDetail(
                body = "{\"csrfToken\":\"$secret\"}",
                statusCode = 200,
            )
        }

        assertEquals("post.info", failure.endpoint)
        assertEquals(200, failure.statusCode)
        assertFalse(secret in failure.rawBody.orEmpty())
        assertTrue("[REDACTED]" in failure.rawBody.orEmpty())
    }
}
