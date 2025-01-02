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
) {
    val planBrowserUrl get() = "https://www.fanbox.cc/@${user?.creatorId}/plans/$id"
    val supportingBrowserUrl get() = "https://www.fanbox.cc/creators/supporting/@${user?.creatorId}"
}