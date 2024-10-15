package me.matsumo.fankt.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxPostListEntity(
    @SerialName("body")
    val body: Body,
) {
    @Serializable
    data class Body(
        @SerialName("items")
        val items: List<FanboxPostEntity>,
        @SerialName("nextUrl")
        val nextUrl: String?,
    )
}