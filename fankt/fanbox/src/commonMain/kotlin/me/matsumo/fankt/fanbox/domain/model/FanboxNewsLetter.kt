@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.id.FanboxNewsLetterId
import kotlin.time.Instant

@Serializable
data class FanboxNewsLetter(
    val id: FanboxNewsLetterId,
    val body: String,
    val createdAt: Instant,
    val creator: FanboxCreator,
    val isRead: Boolean,
)
