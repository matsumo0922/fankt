package me.matsumo.fankt.fanbox

import io.ktor.http.toHttpDate
import io.ktor.util.date.GMTDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FanboxExceptionFactoryTest {

    @Test
    fun fragmentIsRedactedNormalizedThenBounded() {
        val secret = "secret-at-the-truncation-boundary"
        val prefix = "x".repeat(FanboxExceptionFactory.MAX_RAW_BODY_LENGTH)
        val fragment = FanboxExceptionFactory.sanitizeFragment(
            "$prefix\n{\"csrfToken\":\"$secret\"}\u0000tail",
        )

        assertEquals(FanboxExceptionFactory.MAX_RAW_BODY_LENGTH, fragment.length)
        assertTrue(fragment.endsWith('…'))
        assertFalse(secret in fragment)
        assertFalse('\n' in fragment)
        assertFalse('\u0000' in fragment)
    }

    @Test
    fun metadataHtmlCredentialIsRedacted() {
        val fragment = FanboxExceptionFactory.sanitizeFragment(
            "<meta content=\"{&quot;csrfToken&quot;:&quot;fixture-token&quot;}\">",
        )

        assertFalse("fixture-token" in fragment)
        assertTrue("[REDACTED]" in fragment)
    }

    @Test
    fun retryAfterParsesDeltaSecondsBeforeHttpDate() {
        assertEquals(120.seconds, FanboxExceptionFactory.parseRetryAfter("120", 0))
        assertNull(FanboxExceptionFactory.parseRetryAfter("-1", 0))
        assertNull(FanboxExceptionFactory.parseRetryAfter("not-a-date", 0))
    }

    @Test
    fun retryAfterRejectsNonFiniteDeltaSeconds() {
        assertNull(FanboxExceptionFactory.parseRetryAfter(Long.MAX_VALUE.toString(), 0))
    }

    @Test
    fun retryAfterParsesFutureAndPastHttpDates() {
        val now = 1_600_000_000_000L
        val future = GMTDate(now + 10_000).toHttpDate()
        val past = GMTDate(now - 10_000).toHttpDate()

        assertEquals(10.seconds, FanboxExceptionFactory.parseRetryAfter(future, now))
        assertEquals(Duration.ZERO, FanboxExceptionFactory.parseRetryAfter(past, now))
    }

    @Test
    fun retryAfterRejectsFarFutureAndOverflowingHttpDates() {
        val farFuture = "Fri, 31 Dec 9999 23:59:59 GMT"
        val farPast = "Mon, 01 Jan 0001 00:00:00 GMT"

        assertNull(FanboxExceptionFactory.parseRetryAfter(farFuture, Long.MIN_VALUE))
        assertNull(FanboxExceptionFactory.parseRetryAfter(farPast, Long.MAX_VALUE))
    }
}
