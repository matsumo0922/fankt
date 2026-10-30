@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.compatibility

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import me.matsumo.fankt.fanbox.Fanbox
import me.matsumo.fankt.fanbox.FanboxCookieRecord
import me.matsumo.fankt.fanbox.FanboxGuestCache
import me.matsumo.fankt.fanbox.FanboxLogLevel
import me.matsumo.fankt.fanbox.domain.model.FanboxPost
import kotlin.time.Instant

internal class FanboxBoundaryConsumer(guestCache: FanboxGuestCache) {
    val fanbox: Fanbox = Fanbox(
        guestManifestUrl = "https://matsumo0922.github.io/fankt/zipline/v1/manifest.zipline.json",
        guestTrustedKeys = mapOf("fanboxGuest" to ByteArray(32)),
        guestCache = guestCache,
        logLevel = FanboxLogLevel.INFO,
    )
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
