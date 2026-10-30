package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FanboxTag(
    val count: Int,
    val coverImageUrl: String?,
    val name: String,
)
