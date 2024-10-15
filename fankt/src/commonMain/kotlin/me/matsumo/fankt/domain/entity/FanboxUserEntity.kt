package me.matsumo.fankt.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxUserEntity(
    @SerialName("iconUrl")
    val iconUrl: String?,
    @SerialName("name")
    val name: String,
    @SerialName("userId")
    val userId: String,
)