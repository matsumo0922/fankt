package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class FanboxPostListEntity(
    @SerialName("body")
    val body: me.matsumo.fankt.fanbox.domain.entity.FanboxPostListEntity.Body,
) {
    @Serializable
    data class Body(
        @SerialName("items")
        val items: List<JsonElement>,
        @SerialName("nextUrl")
        val nextUrl: String?,
    )
}
