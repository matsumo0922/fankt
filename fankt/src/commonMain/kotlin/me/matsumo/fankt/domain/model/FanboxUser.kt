package me.matsumo.fankt.domain.model

import kotlinx.serialization.Serializable
import me.matsumo.fankt.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.domain.model.id.FanboxUserId

@Serializable
data class FanboxUser(
    val userId: FanboxUserId?,
    val creatorId: FanboxCreatorId?,
    val name: String,
    val iconUrl: String?,
)
