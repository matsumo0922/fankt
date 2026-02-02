package me.matsumo.fankt.fanbox.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class FanboxPaidRecord(
    val id: String,
    val paidAmount: Int,
    val paymentDateTime: Instant,
    val paymentMethod: FanboxPaymentMethod,
    val creator: FanboxCreator,
)
