package me.matsumo.fankt.fanbox.guest

import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The host branches on these three outcomes to decide whether to keep using the guest, so a body
 * landing in the wrong one is not visible as a failure — it changes what the caller sees and
 * whether the delivery path stays alive.
 */
class RealFanboxGuestServiceTest {

    private val service = RealFanboxGuestService()

    @Test
    fun wellFormedResponseParses() {
        val result = service.parsePostDetail(FanboxPostJsonFixtures.postInfoArticleA, statusCode = 200)

        assertTrue(result is GuestParseResult.Success)
    }

    @Test
    fun changedSchemaIsAMismatchRatherThanAGuestFailure() {
        val result = service.parsePostDetail("""{"body":{"unexpected":true}}""", statusCode = 200)

        // GuestFailure here would pin the host to its own path on every FANBOX change, which is
        // exactly the situation this bundle exists to fix.
        assertTrue(result is GuestParseResult.SchemaMismatch)
    }

    @Test
    fun nonJsonBodyIsAMismatchRatherThanAGuestFailure() {
        val result = service.parsePostDetail("<html>maintenance</html>", statusCode = 200)

        assertTrue(result is GuestParseResult.SchemaMismatch)
    }

    @Test
    fun descriptorCarriesTheRequestedPost() {
        val descriptor = service.buildPostDetailRequest("12345")

        assertEquals("post.info", descriptor.path)
        assertTrue(descriptor.query.any { it.name == "postId" && it.value == "12345" })
    }
}
