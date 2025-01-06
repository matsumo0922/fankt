package me.matsumo.fankt.fanbox.domain.model

import kotlinx.datetime.Instant

data class FanboxCreatorPlanDetail(
    val plan: me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlan,
    val supportStartDatetime: String,
    val supportTransactions: List<me.matsumo.fankt.fanbox.domain.model.FanboxCreatorPlanDetail.SupportTransaction>,
    val supporterCardImageUrl: String,
) {
    data class SupportTransaction(
        val id: String,
        val paidAmount: Int,
        val transactionDatetime: Instant,
        val targetMonth: String,
        val user: me.matsumo.fankt.fanbox.domain.model.FanboxUser,
    )
}
