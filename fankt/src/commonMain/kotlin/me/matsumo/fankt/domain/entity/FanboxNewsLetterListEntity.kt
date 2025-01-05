package me.matsumo.fankt.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxNewsLettersEntity(
    @SerialName("body")
    val body: List<Body>,
) {
    @Serializable
    data class Body(
        @SerialName("body")
        val body: String,
        @SerialName("createdAt")
        val createdAt: String,
        @SerialName("creator")
        val creator: FanboxCreatorEntity,
        @SerialName("id")
        val id: String,
        @SerialName("isRead")
        val isRead: Boolean,
    )
}
