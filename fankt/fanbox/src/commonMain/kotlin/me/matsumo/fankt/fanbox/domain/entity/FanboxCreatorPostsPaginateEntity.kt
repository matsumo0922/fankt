package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCreatorPostsPaginateEntity(
    @SerialName("body")
    val body: List<String>,
)
