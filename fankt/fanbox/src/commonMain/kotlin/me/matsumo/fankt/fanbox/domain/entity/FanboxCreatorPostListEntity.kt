package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class FanboxCreatorPostItemsEntity(
    @SerialName("body")
    val body: List<JsonElement>,
)
