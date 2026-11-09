package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 投稿のカバー。画像カバーは [url] を、動画カバーは [url] の代わりに [video] を持つ。 */
@Serializable
internal data class FanboxCoverEntity(
    @SerialName("type")
    val type: String,
    @SerialName("url")
    val url: String? = null,
    @SerialName("video")
    val video: Video? = null,
) {
    /** 動画カバーの動画。 */
    @Serializable
    data class Video(@SerialName("serviceProvider") val serviceProvider: String, @SerialName("videoId") val videoId: String)
}
