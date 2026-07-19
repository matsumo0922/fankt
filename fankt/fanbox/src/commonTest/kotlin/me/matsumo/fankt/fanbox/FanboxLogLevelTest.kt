package me.matsumo.fankt.fanbox

import io.ktor.client.plugins.logging.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class FanboxLogLevelTest {
    @Test
    fun publicLevelsMapToSecurityCappedInternalLevels() {
        assertEquals(LogLevel.NONE, FanboxLogLevel.NONE.toInternalLogLevel())
        assertEquals(LogLevel.INFO, FanboxLogLevel.INFO.toInternalLogLevel())
        assertEquals(LogLevel.HEADERS, FanboxLogLevel.HEADERS.toInternalLogLevel())
        assertEquals(LogLevel.INFO, FanboxLogLevel.BODY.toInternalLogLevel())
        assertEquals(LogLevel.HEADERS, FanboxLogLevel.ALL.toInternalLogLevel())
    }
}
