package me.matsumo.fankt.fanbox.datasource.parser

import me.matsumo.fankt.fanbox.fixture.FanboxMetadataHtmlFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FanboxKsoupMetadataExtractorTest {

    @Test
    fun extractsMetadataContentFromHomepageHtml() {
        val extracted = FanboxKsoupMetadataExtractor.extract(FanboxMetadataHtmlFixtures.home)

        assertEquals(true, extracted?.contains("\"csrfToken\":\"fixture-token\""))
    }

    @Test
    fun returnsNullWhenMetadataMetaElementIsMissing() {
        val html = "<html><body>missing metadata fixture-token</body></html>"

        assertNull(FanboxKsoupMetadataExtractor.extract(html))
    }
}
