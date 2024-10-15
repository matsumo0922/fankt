package me.matsumo.fankt.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCoverEntity(
    @SerialName("type")
    val type: String,
    @SerialName("url")
    val url: String,
)