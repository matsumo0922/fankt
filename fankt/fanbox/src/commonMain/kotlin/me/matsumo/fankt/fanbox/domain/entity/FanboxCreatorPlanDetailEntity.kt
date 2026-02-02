package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxCreatorPlanDetailEntity(
    @SerialName("body")
    val body: Body,
) {
    @Serializable
    data class Body(
        @SerialName("plan")
        val plan: FanboxCreatorPlanListEntity.Body,
        @SerialName("supportStartDatetime")
        val supportStartDatetime: String,
        @SerialName("supportTransactions")
        val supportTransactions: List<SupportTransaction>,
        @SerialName("supporterCardImageUrl")
        val supporterCardImageUrl: String,
    ) {
        @Serializable
        data class SupportTransaction(
            @SerialName("id")
            val id: String,
            @SerialName("paidAmount")
            val paidAmount: Int,
            @SerialName("supporter")
            val supporter: Supporter,
            @SerialName("targetMonth")
            val targetMonth: String,
            @SerialName("transactionDatetime")
            val transactionDatetime: String,
        ) {
            @Serializable
            data class Supporter(
                @SerialName("iconUrl")
                val iconUrl: String?,
                @SerialName("name")
                val name: String,
                @SerialName("userId")
                val userId: String,
            )
        }
    }
}
