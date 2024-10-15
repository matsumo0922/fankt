package me.matsumo.fankt.domain.model

import kotlinx.datetime.Instant

data class FanboxCreatorPlanDetail(
    val plan: FanboxCreatorPlan,
    val supportStartDatetime: String,
    val supportTransactions: List<SupportTransaction>,
    val supporterCardImageUrl: String,
) {
    data class SupportTransaction(
        val id: String,
        val paidAmount: Int,
        val transactionDatetime: Instant,
        val targetMonth: String,
        val user: FanboxUser,
    )
}
