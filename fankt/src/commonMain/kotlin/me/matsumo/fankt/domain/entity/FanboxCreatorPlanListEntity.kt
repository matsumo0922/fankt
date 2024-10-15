package me.matsumo.fankt.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCreatorPlanListEntity(
    @SerialName("body")
    val body: List<Body>,
) {
    @Serializable
    data class Body(
        @SerialName("coverImageUrl")
        val coverImageUrl: String?,
        @SerialName("creatorId")
        val creatorId: String?,
        @SerialName("description")
        val description: String,
        @SerialName("fee")
        val fee: Int,
        @SerialName("hasAdultContent")
        val hasAdultContent: Boolean,
        @SerialName("id")
        val id: String,
        @SerialName("paymentMethod")
        val paymentMethod: String?,
        @SerialName("title")
        val title: String,
        @SerialName("user")
        val user: FanboxUserEntity?,
    )
}
