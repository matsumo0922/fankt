package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxPostEntity(
    @SerialName("commentCount")
    val commentCount: Int,
    @SerialName("cover")
    val cover: me.matsumo.fankt.fanbox.domain.entity.FanboxCoverEntity?,
    @SerialName("creatorId")
    val creatorId: String,
    @SerialName("excerpt")
    val excerpt: String,
    @SerialName("feeRequired")
    val feeRequired: Int,
    @SerialName("hasAdultContent")
    val hasAdultContent: Boolean,
    @SerialName("id")
    val id: String,
    @SerialName("isLiked")
    val isLiked: Boolean,
    @SerialName("isRestricted")
    val isRestricted: Boolean,
    @SerialName("likeCount")
    val likeCount: Int,
    @SerialName("publishedDatetime")
    val publishedDatetime: String,
    @SerialName("tags")
    val tags: List<String>,
    @SerialName("title")
    val title: String,
    @SerialName("updatedDatetime")
    val updatedDatetime: String,
    @SerialName("user")
    val user: FanboxUserEntity?,
)
