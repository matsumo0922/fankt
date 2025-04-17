package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCommentListEntity(
    @SerialName("items")
    val items: List<Item>,
    @SerialName("nextUrl")
    val nextUrl: String?,
) {
    @Serializable
    data class Item(
        @SerialName("body")
        val body: String,
        @SerialName("createdDatetime")
        val createdDatetime: String,
        @SerialName("id")
        val id: String,
        @SerialName("isLiked")
        val isLiked: Boolean,
        @SerialName("isOwn")
        val isOwn: Boolean,
        @SerialName("likeCount")
        val likeCount: Int,
        @SerialName("parentCommentId")
        val parentCommentId: String,
        @SerialName("rootCommentId")
        val rootCommentId: String,
        @SerialName("user")
        val user: FanboxUserEntity?,
        @SerialName("replies")
        val replies: List<Item> = emptyList(),
    )
}

@Serializable
internal data class FanboxCommentBodyEntity(
    @SerialName("viewMode")
    val viewMode: String,
    @SerialName("commentList")
    val commentList: FanboxCommentListEntity,
)
