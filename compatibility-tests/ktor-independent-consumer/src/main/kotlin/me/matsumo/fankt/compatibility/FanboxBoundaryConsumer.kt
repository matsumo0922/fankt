package me.matsumo.fankt.compatibility

import io.ktor.client.HttpClient
import me.matsumo.fankt.fanbox.Fanbox
import me.matsumo.fankt.fanbox.FanboxCookieRecord
import me.matsumo.fankt.fanbox.FanboxLogLevel

internal class FanboxBoundaryConsumer {
    val fanbox: Fanbox = Fanbox(logLevel = FanboxLogLevel.INFO)
    val hostClient: HttpClient = HttpClient()

    suspend fun setSession(value: String) {
        fanbox.setCookies(
            listOf(
                FanboxCookieRecord(
                    domain = "fanbox.cc",
                    path = "/",
                    name = "FANBOXSESSID",
                    value = value,
                    expiresAtEpochMilliseconds = null,
                    secure = true,
                    hostOnly = false,
                ),
            ),
        )
    }
}
