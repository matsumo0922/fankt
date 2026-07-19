package me.matsumo.fankt.compatibility

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import me.matsumo.fankt.fanbox.Fanbox
import me.matsumo.fankt.fanbox.FanboxCookieRecord
import me.matsumo.fankt.fanbox.FanboxLogLevel
import me.matsumo.fankt.fanbox.domain.model.FanboxPost

internal class FanboxBoundaryConsumer {
    val fanbox: Fanbox = Fanbox(logLevel = FanboxLogLevel.INFO)
    val cookies: Flow<List<FanboxCookieRecord>> = fanbox.cookies

    fun publishedAt(post: FanboxPost): Instant = post.publishedDatetime

    fun postSerializer(): KSerializer<FanboxPost> = FanboxPost.serializer()

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
