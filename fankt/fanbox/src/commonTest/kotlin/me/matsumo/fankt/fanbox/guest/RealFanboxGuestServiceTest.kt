package me.matsumo.fankt.fanbox.guest

import me.matsumo.fankt.fanbox.fixture.FanboxPostJsonFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The host turns these three outcomes into what the caller sees, so a body landing in the wrong one
 * is not visible as a failure — it changes the exception a caller handles.
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

        // GuestFailure here would report every FANBOX change as a broken guest rather than as the
        // schema change it is.
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
