package me.matsumo.fankt.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import me.matsumo.fankt.domain.model.id.FanboxPostId

@Serializable
data class FanboxPost(
    val id: FanboxPostId,
    val title: String,
    val cover: FanboxCover?,
    val user: FanboxUser?,
    val excerpt: String,
    val feeRequired: Int,
    val hasAdultContent: Boolean,
    val isLiked: Boolean,
    val isRestricted: Boolean,
    val likeCount: Int,
    val commentCount: Int,
    val tags: List<String>,
    val publishedDatetime: Instant,
    val updatedDatetime: Instant,
)
