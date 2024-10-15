package me.matsumo.fankt.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxBellListEntity(
    @SerialName("body")
    val body: Body,
) {
    @Serializable
    data class Body(
        @SerialName("items")
        val items: List<Item>,
        @SerialName("nextUrl")
        val nextUrl: String?,
    ) {
        @Serializable
        data class Item(
            @SerialName("count")
            val count: Int?,
            @SerialName("creatorId")
            val creatorId: String?,
            @SerialName("creatorUserId")
            val creatorUserId: String?,
            @SerialName("id")
            val id: String,
            @SerialName("isRootComment")
            val isRootComment: Boolean?,
            @SerialName("isUnread")
            val isUnread: Boolean,
            @SerialName("notifiedDatetime")
            val notifiedDatetime: String,
            @SerialName("post")
            val post: FanboxPostEntity?,
            @SerialName("postCommentBody")
            val postCommentBody: String?,
            @SerialName("postId")
            val postId: String?,
            @SerialName("postTitle")
            val postTitle: String?,
            @SerialName("type")
            val type: String,
            @SerialName("userName")
            val userName: String?,
            @SerialName("userProfileImg")
            val userProfileImg: String?,
        )
    }
}
