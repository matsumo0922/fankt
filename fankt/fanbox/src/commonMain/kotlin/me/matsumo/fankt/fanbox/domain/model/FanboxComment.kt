package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCommentId
import kotlin.time.Instant

@Serializable
data class FanboxComment(
    val body: String,
    val createdDatetime: Instant,
    val id: FanboxCommentId,
    val isLiked: Boolean,
    val isOwn: Boolean,
    val likeCount: Int,
    val parentCommentId: FanboxCommentId,
    val rootCommentId: FanboxCommentId,
    val replies: List<FanboxComment>,
    val user: FanboxUser?,
)
