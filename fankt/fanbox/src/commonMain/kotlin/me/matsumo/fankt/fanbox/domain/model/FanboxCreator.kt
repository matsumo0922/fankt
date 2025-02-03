package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId

@Serializable
data class FanboxCreator(
    val creatorId: FanboxCreatorId?,
    val user: FanboxUser?,
)
