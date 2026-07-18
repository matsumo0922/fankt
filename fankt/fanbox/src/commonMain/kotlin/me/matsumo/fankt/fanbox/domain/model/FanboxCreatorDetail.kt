package me.matsumo.fankt.fanbox.domain.model

import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId

@Serializable
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
    val user: FanboxUser?,
) {
    val supportingBrowserUrl get() = "https://www.fanbox.cc/creators/supporting/@${user?.creatorId}"

    /** A profile item classified from the `creator.get` item type. */
    @Serializable
    sealed interface ProfileItem {

        /** An image profile item. Both URLs retain the nullable FANBOX response values. */
        @Serializable
        @SerialName("image")
        data class Image(
            val id: String,
            val imageUrl: String?,
            val thumbnailUrl: String?,
        ) : ProfileItem

        /**
         * A video profile item.
         *
         * [url] reconstructs only the known YouTube and Vimeo URL forms. The provider, [videoId],
         * and resulting URL remain untrusted network data. Callers must validate them against their
         * navigation policy before opening the URL.
         */
        @Serializable
        @SerialName("video")
        data class Video(
            val id: String,
            val serviceProvider: String,
            val videoId: String,
            val thumbnailUrl: String?,
        ) : ProfileItem {
            val url: String?
                get() = when (serviceProvider) {
                    "youtube" -> "https://www.youtube.com/watch?v=$videoId"
                    "vimeo" -> "https://vimeo.com/$videoId"
                    else -> null
                }
        }

        /**
         * A profile item that cannot be represented by a known variant.
         *
         * [rawJson] is untrusted network data retained for forward handling and diagnostics. Callers
         * must validate and sanitize it before parsing, displaying, or using any contained URL.
         */
        @Serializable
        @SerialName("unknown")
        data class Unknown(
            val id: String?,
            @SerialName("itemType")
            val type: String,
            val rawJson: String,
        ) : ProfileItem
    }

    @Serializable
    data class ProfileLink(
        val url: String,
        val link: Platform,
    )

    @Serializable
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
