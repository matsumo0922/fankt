package me.matsumo.fankt.domain.model

import kotlinx.datetime.Instant
import me.matsumo.fankt.domain.model.id.FanboxNewsLetterId

data class FanboxNewsLetter(
    val id: FanboxNewsLetterId,
    val body: String,
    val createdAt: Instant,
    val creator: FanboxCreator,
    val isRead: Boolean,
)
