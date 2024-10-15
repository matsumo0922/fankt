package me.matsumo.fankt.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FanboxCover(
    val url: String,
    val type: String,
)
