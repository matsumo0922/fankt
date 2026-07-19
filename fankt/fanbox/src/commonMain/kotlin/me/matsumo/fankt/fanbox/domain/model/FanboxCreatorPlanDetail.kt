@file:OptIn(kotlin.time.ExperimentalTime::class)

package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class FanboxCreatorPlanDetail(
    val plan: FanboxCreatorPlan,
    val supportStartDatetime: String,
    val supportTransactions: List<SupportTransaction>,
    val supporterCardImageUrl: String,
) {
    @Serializable
    data class SupportTransaction(
        val id: String,
        val paidAmount: Int,
        val transactionDatetime: Instant,
        val targetMonth: String,
        val user: FanboxUser,
    )
}
