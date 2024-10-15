package me.matsumo.fankt.domain.model

import me.matsumo.fankt.domain.model.id.FanboxCreatorId

data class FanboxCreator(
    val creatorId: FanboxCreatorId?,
    val user: FanboxUser,
)
