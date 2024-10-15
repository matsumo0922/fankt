package me.matsumo.fankt.domain.model

import kotlinx.serialization.Serializable
import me.matsumo.fankt.domain.model.id.FanboxCreatorId

@Serializable
data class FanboxUser(
    val userId: String,
    val name: String,
    val iconUrl: String?,
)
