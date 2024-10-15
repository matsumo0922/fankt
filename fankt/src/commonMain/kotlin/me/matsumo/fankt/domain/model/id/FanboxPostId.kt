package me.matsumo.fankt.domain.model.id

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class FanboxPostId(val value: String) {

    @OptIn(ExperimentalUuidApi::class)
    val uniqueValue: String = "post-$value-${Uuid.random()}"

    override fun toString(): String = value
}
