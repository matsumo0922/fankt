package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxPostListEntity(
    @SerialName("body")
    val body: me.matsumo.fankt.fanbox.domain.entity.FanboxPostListEntity.Body,
) {
    @Serializable
    data class Body(
        @SerialName("items")
        val items: List<me.matsumo.fankt.fanbox.domain.entity.FanboxPostEntity>,
        @SerialName("nextUrl")
        val nextUrl: String?,
    )
}
