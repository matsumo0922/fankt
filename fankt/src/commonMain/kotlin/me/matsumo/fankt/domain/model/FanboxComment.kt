package me.matsumo.fankt.domain.model

import kotlinx.datetime.Instant
import me.matsumo.fankt.domain.model.id.FanboxCommentId

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