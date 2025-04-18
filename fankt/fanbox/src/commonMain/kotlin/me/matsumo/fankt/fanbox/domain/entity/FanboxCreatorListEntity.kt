package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCreatorListEntity(
    @SerialName("body")
    val body: List<FanboxCreatorDetailEntity.Body>,
)
