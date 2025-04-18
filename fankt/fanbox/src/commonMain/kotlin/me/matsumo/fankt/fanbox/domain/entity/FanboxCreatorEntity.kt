package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCreatorEntity(
    @SerialName("creatorId")
    val creatorId: String?,
    @SerialName("user")
    val user: FanboxUserEntity?,
)
