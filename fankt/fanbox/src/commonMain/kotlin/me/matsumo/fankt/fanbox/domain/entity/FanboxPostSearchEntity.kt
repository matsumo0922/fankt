package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxPostSearchEntity(
    @SerialName("body")
    val body: Body,
) {
    @Serializable
    data class Body(
        @SerialName("count")
        val count: Int,
        @SerialName("items")
        val items: List<FanboxPostEntity>,
        @SerialName("nextUrl")
        val nextUrl: String?,
    )
}
