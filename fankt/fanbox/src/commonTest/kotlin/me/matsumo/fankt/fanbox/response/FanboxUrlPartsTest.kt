package me.matsumo.fankt.fanbox.response

import me.matsumo.fankt.fanbox.domain.model.FanboxCreatorDetail
import me.matsumo.fankt.fanbox.domain.translateToCursor
import me.matsumo.fankt.fanbox.endpoint.FanboxEndpoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FanboxUrlPartsTest {

    @Test
    fun queryValuesDecodeExactlyOnceAndKeepEncodedSeparatorsInsideValues() {
        val rawUrl =
            "https://api.fanbox.cc/post.listHome?cursor=a%26b%3Dc%2520d&plus=a+b&empty=&missing&dup=first&dup=second"
        val url = FanboxUrlParts.parse(rawUrl)

        assertEquals("a&b=c%20d", url.queryValue("cursor"))
        assertEquals("a b", url.queryValue("plus"))
        assertEquals("", url.queryValue("empty"))
        assertEquals(null, url.queryValue("missing"))
        assertEquals("first", url.queryValue("dup"))
    }

    @Test
    fun cursorExtractsOnlyAllowlistedQueryAndRebuildsKnownRelativeRoute() {
        val cursor = (
            "https://untrusted.example/escape?firstPublishedDatetime=2000-01-01T00%3A00%3A00%2B00%3A00" +
                "&maxPublishedDatetime=max%26value&firstId=id%3Done&maxId=id%2520two&limit=25&path=ignored"
            ).translateToCursor()
        val descriptor = FanboxEndpoints.homePosts(cursor)

        assertEquals("post.listHome", descriptor.path)
        assertEquals("2000-01-01T00:00:00+00:00", cursor.firstPublishedDatetime)
        assertEquals("max&value", cursor.maxPublishedDatetime)
        assertEquals("id=one", cursor.firstId)
        assertEquals("id%20two", cursor.maxId)
        assertEquals(25, cursor.limit)
        assertEquals(
            listOf("limit", "firstPublishedDatetime", "maxPublishedDatetime", "firstId", "maxId"),
            descriptor.query.map { it.name },
        )
    }

    @Test
    fun malformedPercentEscapesAndUtf8AreRejected() {
        assertFailsWith<IllegalArgumentException> {
            FanboxUrlParts.parse("https://api.fanbox.cc/post.listHome?cursor=%")
        }
        assertFailsWith<IllegalArgumentException> {
            FanboxUrlParts.parse("https://api.fanbox.cc/post.listHome?cursor=%GG")
        }
        assertFailsWith<IllegalArgumentException> {
            FanboxUrlParts.parse("https://api.fanbox.cc/post.listHome?cursor=%FF")
        }
    }

    @Test
    fun hostExtractionIsCaseInsensitiveAtPlatformMappingBoundary() {
        val parts = FanboxUrlParts.parse("HTTPS://WWW.YouTube.Com/watch?v=fixture")

        assertEquals("WWW.YouTube.Com", parts.host)
        assertEquals(FanboxCreatorDetail.Platform.YOUTUBE, FanboxCreatorDetail.Platform.fromUrl("HTTPS://WWW.YouTube.Com/watch"))
        assertEquals(FanboxCreatorDetail.Platform.UNKNOWN, FanboxCreatorDetail.Platform.fromUrl("not an absolute url"))
    }
}
