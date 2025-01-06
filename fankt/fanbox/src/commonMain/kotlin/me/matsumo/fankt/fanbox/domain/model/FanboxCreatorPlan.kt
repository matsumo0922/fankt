package me.matsumo.fankt.fanbox.domain.model

import me.matsumo.fankt.fanbox.domain.model.id.FanboxPlanId

data class FanboxCreatorPlan(
    val id: FanboxPlanId,
    val title: String,
    val description: String,
    val fee: Int,
    val coverImageUrl: String?,
    val hasAdultContent: Boolean,
    val paymentMethod: me.matsumo.fankt.fanbox.domain.model.FanboxPaymentMethod,
    val user: me.matsumo.fankt.fanbox.domain.model.FanboxUser?,
) {
    val planBrowserUrl get() = "https://www.fanbox.cc/@${user?.creatorId}/plans/$id"
    val supportingBrowserUrl get() = "https://www.fanbox.cc/creators/supporting/@${user?.creatorId}"
}
