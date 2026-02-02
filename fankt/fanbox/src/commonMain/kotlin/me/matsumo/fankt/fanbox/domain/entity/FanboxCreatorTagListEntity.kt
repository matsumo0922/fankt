package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCreatorTagListEntity(
    @SerialName("body")
    val body: Body,
) {
    @Serializable
    data class Body(
        @SerialName("featuredTags")
        val featuredTags: List<FeaturedTags>,
    )

    @Serializable
    data class FeaturedTags(
        @SerialName("count")
        val count: Int,
        @SerialName("coverImageUrl")
        val coverImageUrl: String?,
        @SerialName("tag")
        val tag: String,
    )
}
