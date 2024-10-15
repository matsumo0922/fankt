package me.matsumo.fankt.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCreatorSearchListEntity(
    @SerialName("body")
    val body: Body,
) {
    @Serializable
    data class Body(
        @SerialName("count")
        val count: Int,
        @SerialName("creators")
        val creators: List<FanboxCreatorDetailEntity.Body>,
        @SerialName("nextPage")
        val nextPage: Int?,
    )
}
