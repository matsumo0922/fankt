@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class FanboxPaidRecord(
    val id: String,
    val paidAmount: Int,
    val paymentDateTime: Instant,
    val paymentMethod: FanboxPaymentMethod,
    val creator: FanboxCreator,
)
