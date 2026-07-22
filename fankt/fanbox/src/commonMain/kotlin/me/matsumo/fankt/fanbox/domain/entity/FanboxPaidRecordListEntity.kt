package me.matsumo.fankt.fanbox.domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FanboxPaidRecordListEntity(
    @SerialName("body")
    val body: Body,
) {
    @Serializable
    data class Body(
        @SerialName("payments")
        val payments: List<Payment>,
    )

    @Serializable
    data class Payment(
        @SerialName("creator")
        val creator: FanboxCreatorEntity,
        @SerialName("id")
        val id: String,
        @SerialName("paidAmount")
        val paidAmount: Int,
        @SerialName("paymentDatetime")
        val paymentDatetime: String,
        @SerialName("paymentMethod")
        val paymentMethod: String,
    )
}
