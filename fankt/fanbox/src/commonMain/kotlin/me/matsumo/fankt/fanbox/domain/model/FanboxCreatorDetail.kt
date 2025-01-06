package me.matsumo.fankt.fanbox.domain.model

import io.ktor.http.Url
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId

data class FanboxCreatorDetail(
    val creatorId: FanboxCreatorId,
    val coverImageUrl: String?,
    val description: String,
    val hasAdultContent: Boolean,
    val hasBoothShop: Boolean,
    val isAcceptingRequest: Boolean,
    val isFollowed: Boolean,
    val isStopped: Boolean,
    val isSupported: Boolean,
    val profileItems: List<ProfileItem>,
    val profileLinks: List<ProfileLink>,
    val user: me.matsumo.fankt.fanbox.domain.model.FanboxUser?,
) {
    val supportingBrowserUrl get() = "https://www.fanbox.cc/creators/supporting/@${user?.creatorId}"

    data class ProfileItem(
        val id: String,
        val imageUrl: String?,
        val thumbnailUrl: String?,
        val type: String,
    )

    data class ProfileLink(
        val url: String,
        val link: Platform,
    )

    enum class Platform {
        BOOTH,
        FACEBOOK,
        FANZA,
        INSTAGRAM,
        LINE,
        PIXIV,
        TUMBLR,
        TWITTER,
        YOUTUBE,
        UNKNOWN,
        ;

        companion object {
            fun fromUrl(url: String): Platform {
                val hostname = Url(url).host.lowercase()

                return when {
                    "booth.pm" in hostname -> BOOTH
                    "facebook.com" in hostname -> FACEBOOK
                    "dmm.co.jp" in hostname -> FANZA
                    "instagram.com" in hostname -> INSTAGRAM
                    "line.me" in hostname -> LINE
                    listOf("www.pixiv.net", "touch.pixiv.net", "pixiv.me", "fanbox.cc").any { it in hostname } -> PIXIV
                    "tumblr.com" in hostname -> TUMBLR
                    "twitter.com" in hostname -> TWITTER
                    listOf("youtu.be", "youtube.com").any { it in hostname } -> YOUTUBE
                    else -> UNKNOWN
                }
            }
        }
    }
}
