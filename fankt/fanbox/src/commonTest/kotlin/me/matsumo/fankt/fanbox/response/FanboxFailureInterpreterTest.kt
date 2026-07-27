package me.matsumo.fankt.fanbox.response

import me.matsumo.fankt.fanbox.FanboxException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FanboxFailureInterpreterTest {

    @Test
    fun primitiveHttpFailuresKeepTypedExceptionSemantics() {
        assertIs<FanboxException.Unauthorized>(failure(401))
        assertIs<FanboxException.Forbidden>(failure(403))
        assertIs<FanboxException.NotFound>(failure(404))
        assertIs<FanboxException.RateLimited>(failure(429))
        assertIs<FanboxException.ServerError>(failure(503))
        assertIs<FanboxException.UnexpectedHttpError>(failure(418))
    }

    @Test
    fun diagnosticsAreSanitizedAndBoundedBeforeExposure() {
        val secret = "credential-that-must-not-survive"
        val failure = FanboxFailureInterpreter.httpFailure(
            endpoint = "post.info",
            statusCode = 500,
            rawBody = "x".repeat(FanboxDiagnostics.MAX_FRAGMENT_LENGTH) + "{\"csrfToken\":\"$secret\"}",
            retryAfter = null,
            nowEpochMilliseconds = 0,
        )

        assertEquals(FanboxDiagnostics.MAX_FRAGMENT_LENGTH, failure.rawBody?.length)
        assertFalse(secret in failure.rawBody.orEmpty())
        assertEquals("post.info", failure.endpoint)
    }

    @Test
    fun retryAfterSupportsDeltaSecondsAndImfFixdateWithoutKtor() {
        val now = 1_600_000_000_000L

        assertEquals(120.seconds, FanboxFailureInterpreter.parseRetryAfter("120", now))
        assertEquals(10.seconds, FanboxFailureInterpreter.parseRetryAfter("Sun, 13 Sep 2020 12:26:50 GMT", now))
        assertEquals(Duration.ZERO, FanboxFailureInterpreter.parseRetryAfter("Sun, 13 Sep 2020 12:26:30 GMT", now))
        assertNull(FanboxFailureInterpreter.parseRetryAfter("-1", now))
        assertNull(FanboxFailureInterpreter.parseRetryAfter("Sunday, 13-Sep-20 12:26:50 GMT", now))
        assertNull(FanboxFailureInterpreter.parseRetryAfter("Fri, 31 Dec 9999 23:59:59 GMT", Long.MIN_VALUE))
    }

    @Test
    fun targetClassificationUsesOnlyHostAndAllowlistedPath() {
        assertEquals(
            FanboxDiagnosticTarget("post.info", true),
            FanboxFailureInterpreter.target(false, "https://API.FANBOX.CC/post.info?secret=value"),
        )
        assertEquals(
            FanboxDiagnosticTarget("custom-request", false),
            FanboxFailureInterpreter.target(false, "https://api.fanbox.cc/private/route"),
        )
        assertEquals(
            FanboxDiagnosticTarget("download", false),
            FanboxFailureInterpreter.target(true, "https://api.fanbox.cc/post.info"),
        )
    }

    private fun failure(statusCode: Int): FanboxException = FanboxFailureInterpreter.httpFailure(
        endpoint = "post.info",
        statusCode = statusCode,
        rawBody = "body",
        retryAfter = "30",
        nowEpochMilliseconds = 0,
    )
}
