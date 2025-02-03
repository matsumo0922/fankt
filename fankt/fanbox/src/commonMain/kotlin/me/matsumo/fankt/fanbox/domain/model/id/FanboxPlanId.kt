package me.matsumo.fankt.fanbox.domain.model.id

import kotlinx.serialization.Serializable

@Serializable
data class FanboxPlanId(val value: String) {
    override fun toString(): String = value
}
