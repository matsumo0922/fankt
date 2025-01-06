package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxTagListEntity(
    @SerialName("body")
    val body: List<Body>,
) {
    @Serializable
    data class Body(
        @SerialName("count")
        val count: Int,
        @SerialName("value")
        val value: String,
    )
}
