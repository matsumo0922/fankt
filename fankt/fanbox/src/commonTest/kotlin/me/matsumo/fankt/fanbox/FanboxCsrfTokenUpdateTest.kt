package me.matsumo.fankt.fanbox

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FanboxCsrfTokenUpdateTest {

    @Test
    fun metadataFailurePropagatesWithoutStoringToken() = runBlocking {
        val failure = FanboxException.SchemaMismatch(
            statusCode = 200,
            rawBody = "bounded",
            endpoint = "homepage",
            cause = null,
        )
        var insertCount = 0

        val actual = try {
            fetchAndStoreCsrfToken(
                fetchMetadata = { throw failure },
                storeToken = { insertCount += 1 },
                nowEpochMilliseconds = { 123L },
            )
            error("Expected metadata fetch to fail")
        } catch (error: FanboxException.SchemaMismatch) {
            error
        }

        assertSame(failure, actual)
        assertEquals(0, insertCount)
    }
}
