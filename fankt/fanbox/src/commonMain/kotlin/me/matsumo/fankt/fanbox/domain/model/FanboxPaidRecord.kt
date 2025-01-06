package me.matsumo.fankt.fanbox.domain.model

import kotlinx.datetime.Instant

data class FanboxPaidRecord(
    val id: String,
    val paidAmount: Int,
    val paymentDateTime: Instant,
    val paymentMethod: me.matsumo.fankt.fanbox.domain.model.FanboxPaymentMethod,
    val creator: me.matsumo.fankt.fanbox.domain.model.FanboxCreator,
)
