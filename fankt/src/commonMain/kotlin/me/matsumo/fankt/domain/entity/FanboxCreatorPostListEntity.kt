package me.matsumo.fankt.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCreatorPostListEntity(
    @SerialName("body")
    val body: List<FanboxPostEntity>,
)
