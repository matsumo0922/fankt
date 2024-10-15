package me.matsumo.fankt.domain.model.id

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class FanboxCommentId(val value: String) {

    @OptIn(ExperimentalUuidApi::class)
    val uniqueValue: String = "comment-$value-${Uuid.random()}"

    override fun toString(): String = value
}
