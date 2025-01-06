package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId

@Serializable
data class FanboxUser(
    val userId: FanboxUserId?,
    val creatorId: FanboxCreatorId?,
    val name: String,
    val iconUrl: String?,
)
