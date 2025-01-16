package me.matsumo.fankt.fanbox.domain.model.id

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class FanboxPostItemId(val value: String) {

    @OptIn(ExperimentalUuidApi::class)
    val uniqueValue: String = "post-item-$value-${Uuid.random()}"

    override fun toString(): String = value
}
