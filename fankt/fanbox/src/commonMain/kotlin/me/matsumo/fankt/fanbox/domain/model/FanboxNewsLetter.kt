package me.matsumo.fankt.fanbox.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.id.FanboxNewsLetterId

@Serializable
data class FanboxNewsLetter(
    val id: FanboxNewsLetterId,
    val body: String,
    val createdAt: Instant,
    val creator: FanboxCreator,
    val isRead: Boolean,
)
