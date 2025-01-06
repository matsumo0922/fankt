package me.matsumo.fankt.fanbox.domain.model

import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId

data class FanboxCreator(
    val creatorId: FanboxCreatorId?,
    val user: me.matsumo.fankt.fanbox.domain.model.FanboxUser?,
)
