package me.matsumo.fankt.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCreatorDetailEntity(
    @SerialName("body")
    val body: Body,
) {
    @Serializable
    data class Body(
        @SerialName("coverImageUrl")
        val coverImageUrl: String?,
        @SerialName("creatorId")
        val creatorId: String,
        @SerialName("description")
        val description: String,
        @SerialName("hasAdultContent")
        val hasAdultContent: Boolean,
        @SerialName("hasBoothShop")
        val hasBoothShop: Boolean,
        @SerialName("isAcceptingRequest")
        val isAcceptingRequest: Boolean,
        @SerialName("isFollowed")
        val isFollowed: Boolean,
        @SerialName("isStopped")
        val isStopped: Boolean,
        @SerialName("isSupported")
        val isSupported: Boolean,
        @SerialName("profileItems")
        val profileItems: List<ProfileItem>,
        @SerialName("profileLinks")
        val profileLinks: List<String>,
        @SerialName("user")
        val user: FanboxUserEntity?,
    ) {
        @Serializable
        data class ProfileItem(
            @SerialName("id")
            val id: String,
            @SerialName("imageUrl")
            val imageUrl: String?,
            @SerialName("thumbnailUrl")
            val thumbnailUrl: String?,
            @SerialName("type")
            val type: String,
        )
    }
}
