package me.matsumo.fankt.domain.model

import me.matsumo.fankt.domain.model.id.FanboxPlanId

data class FanboxCreatorPlan(
    val id: FanboxPlanId,
    val title: String,
    val description: String,
    val fee: Int,
    val coverImageUrl: String?,
    val hasAdultContent: Boolean,
    val paymentMethod: FanboxPaymentMethod,
    val user: FanboxUser?,
)
