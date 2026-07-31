package me.matsumo.fankt.fanbox.datasource.parser

import me.matsumo.fankt.fanbox.FanboxException
import me.matsumo.fankt.fanbox.createFanboxJson
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxCreatorMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxPostMapper
import me.matsumo.fankt.fanbox.datasource.mapper.FanboxUserMapper
import me.matsumo.fankt.fanbox.domain.model.FanboxMetaData
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FanboxMetadataParserGoldenTest {

    @Test
    fun extractedMetadataDecodesAndMapsFullObject() {
        // The Ksoup-extracted `content` attribute of FanboxMetadataHtmlFixtures.home, unescaped.
        val extractedJson = "{\"apiUrl\":\"https://api.example.invalid\",\"context\":{\"privacyPolicy\":" +
            "{\"policyUrl\":\"https://example.invalid/privacy\",\"revisionHistoryUrl\":" +
            "\"https://example.invalid/privacy/history\",\"shouldShowNotice\":false,\"updateDate\":" +
            "\"2000-04-01\"},\"user\":{\"creatorId\":null,\"fanboxUserStatus\":0,\"hasAdultContent\":null," +
            "\"hasUnpaidPayments\":false,\"iconUrl\":null,\"isCreator\":false,\"isMailAddressOutdated\":false," +
            "\"isSupporter\":false,\"lang\":\"fixture-lang\",\"name\":\"Fixture Metadata User\"," +
            "\"planCount\":0,\"showAdultContent\":false,\"userId\":\"93000001\"}},\"csrfToken\":\"fixture-token\"}"
        val entity = FanboxMetadataParser(
            formatter = createFanboxJson(),
            extractor = FanboxMetadataExtractor { extractedJson },
        ).parse(html = "<html><body>unused</body></html>")
        val actual = FanboxUserMapper(
            postMapper = FanboxPostMapper(),
            creatorMapper = FanboxCreatorMapper(),
        ).map(entity)

        val expected = FanboxMetaData(
            apiUrl = "https://api.example.invalid",
            context = FanboxMetaData.Context(
                privacyPolicy = FanboxMetaData.Context.PrivacyPolicy(
                    policyUrl = "https://example.invalid/privacy",
                    revisionHistoryUrl = "https://example.invalid/privacy/history",
                    shouldShowNotice = false,
                    updateDate = "2000-04-01",
                ),
                user = FanboxMetaData.Context.User(
                    creatorId = null,
                    fanboxUserStatus = 0,
                    hasAdultContent = false,
                    hasUnpaidPayments = false,
                    iconUrl = null,
                    isCreator = false,
                    isMailAddressOutdated = false,
                    isSupporter = false,
                    lang = "fixture-lang",
                    name = "Fixture Metadata User",
                    planCount = 0,
                    showAdultContent = false,
                    userId = FanboxUserId(93000001),
                ),
            ),
            csrfToken = "fixture-token",
        )

        assertEquals(expected, actual)
    }

    @Test
    fun missingMetadataUsesSchemaMismatchWithBoundedContext() {
        val html = "<html><body>missing metadata fixture-token</body></html>"

        val error = assertFailsWith<FanboxException.SchemaMismatch> {
            FanboxMetadataParser(
                formatter = createFanboxJson(),
                extractor = FanboxMetadataExtractor { null },
            ).parse(html, statusCode = 200)
        }

        assertEquals(200, error.statusCode)
        assertEquals("homepage", error.endpoint)
        assertEquals(html, error.rawBody)
    }

    @Test
    fun invalidMetadataUsesSchemaMismatchAndRedactsCsrfToken() {
        val html =
            "<meta name=\"metadata\" " +
                "content=\"{&quot;csrfToken&quot;:&quot;fixture-token&quot;,&quot;broken&quot;:}\">"
        val brokenJson = "{\"csrfToken\":\"fixture-token\",\"broken\":}"

        val error = assertFailsWith<FanboxException.SchemaMismatch> {
            FanboxMetadataParser(
                formatter = createFanboxJson(),
                extractor = FanboxMetadataExtractor { brokenJson },
            ).parse(html, statusCode = 200)
        }

        assertEquals(200, error.statusCode)
        assertEquals("homepage", error.endpoint)
        assertFalse("fixture-token" in error.rawBody.orEmpty())
        assertFalse("fixture-token" in error.message.orEmpty())
    }
}
