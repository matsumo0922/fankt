package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class FanboxCreatorPlanListEntity(
    @SerialName("body")
    val body: Body,
) {
    @Serializable
    data class Body(
        @SerialName("plans")
        val plans: List<JsonElement>,
    )

    @Serializable
    data class Plan(
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

@Serializable
internal data class FanboxCreatorPlanListStrictEntity(
    @SerialName("body")
    val body: Body,
) {
    @Serializable
    data class Body(
        @SerialName("plans")
        val plans: List<FanboxCreatorPlanListEntity.Plan>,
    )

    init {
        body.plans.forEachIndexed { index, plan ->
            val userId = plan.user?.userId ?: return@forEachIndexed
            require(userId.toLongOrNull() != null) { "body.plans[$index].user.userId must be a Long" }
        }
    }
}
