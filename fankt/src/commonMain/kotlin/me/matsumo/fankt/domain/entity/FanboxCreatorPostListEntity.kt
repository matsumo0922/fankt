package me.matsumo.fankt.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCreatorPostItemsEntity(
    @SerialName("body")
    val body: List<FanboxPostEntity>,
)
