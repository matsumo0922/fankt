package me.matsumo.fankt.fanbox.domain.model.id

import kotlinx.serialization.Serializable

@Serializable
data class FanboxUserId(val value: Long) {
    override fun toString(): String = value.toString()
}
