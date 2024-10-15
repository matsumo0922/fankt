package me.matsumo.fankt.domain.model

import kotlinx.datetime.Instant

data class FanboxPaidRecord(
    val id: String,
    val paidAmount: Int,
    val paymentDateTime: Instant,
    val paymentMethod: FanboxPaymentMethod,
    val creator: FanboxCreator,
)
